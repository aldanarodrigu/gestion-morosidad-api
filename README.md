# gestion-morosidad-api

Backend del Sistema de Gestión de Morosidad (Spring Boot 4, Java 21, PostgreSQL).
Los datos de deuda vienen de la API de solo lectura **UTEC GeoPagos** de la Intendencia y se
copian a la base propia mediante la [sincronización](#sincronización-con-geopagos).

## Configuración (`.env`)

Copiar `.env.example` a `.env` en la raíz del proyecto y completarlo. La aplicación lo lee al
arrancar (`spring.config.import`). **`.env` está en `.gitignore`: nunca commitearlo**, ni copiar
valores reales a `.env.example`.

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Sí | Conexión a PostgreSQL |
| `DB_SSLMODE` | No | `require` para Supabase, `disable` o `prefer` para una base local |
| `DB_POOL_SIZE` | No | Conexiones por instancia (por defecto 3, ver [Supabase](#base-en-supabase)) |
| `UTEC_API_USERNAME`, `UTEC_API_PASSWORD` | Para sincronizar | Credenciales de GeoPagos (las entrega la Intendencia) |
| `JWT_SECRET` | Sí | Clave para firmar los tokens. **En Base64 y de al menos 32 bytes**; sin ella la API no arranca |
| `SINCRONIZACION_AUTOMATICA`, `SINCRONIZACION_CRON` | No | Sincronización diaria (ver más abajo) |
| `DB_HOST_PORT`, `API_HOST_PORT`, `FRONT_HOST_PORT` | No | Puertos del `docker-compose` |

Generar un `JWT_SECRET` en PowerShell:

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)
```

Las variables de entorno del sistema tienen prioridad sobre el `.env` (útil para apuntar a otra
base sin editar el archivo).

## Ejecutar

### Base local en Docker (recomendado para desarrollar)

Sin latencia, sin límite de conexiones y sin sacar datos de contribuyentes de la máquina:

```bash
docker run -d --name morosidad-local -e POSTGRES_DB=morosidad -e POSTGRES_USER=morosidad \
  -e POSTGRES_PASSWORD=morosidad-local -v morosidad-local-data:/var/lib/postgresql/data \
  -p 127.0.0.1:5434:5432 postgres:17-alpine
```

Y en el `.env`:

```
DB_HOST=localhost
DB_PORT=5434
DB_NAME=morosidad
DB_USER=morosidad
DB_PASSWORD=morosidad-local
DB_SSLMODE=disable
```

Los datos persisten en el volumen `morosidad-local-data` aunque se reinicie el contenedor
(`docker start morosidad-local`). Flyway crea las tablas al arrancar la API.

### Base en Supabase

El plan gratuito admite **15 conexiones en total** entre todos los que usan la base. Por eso cada
instancia de la API usa como máximo 3 (`DB_POOL_SIZE`). Si al arrancar aparece
`max clients reached in session mode`, la base está llena: alguien tiene la API levantada con
una versión vieja (10 conexiones). Cada consulta a Supabase tarda ~170 ms desde Uruguay.

Antes de sincronizar datos reales de contribuyentes en Supabase (servidores en EE.UU.), confirmar
que está permitido por la Ley 18.331 y por el requerimiento de funcionar en la red interna.

### Desde el IDE

Correr la aplicación (Run) con el `.env` completo. Si falla el puerto 8080, agregar
`server.port=9191` al `.env` (ver [Windows](#windows-bind-an-attempt-was-made-to-access-a-socket-in-a-way-forbidden)).

### Todo con Docker Compose

Requiere el repo del frontend clonado al lado de este:

```
NetBeansProjects/
  gestion-morosidad-api/
  gestion-morosidad-frontend/
```

```bash
docker compose up --build -d
```

| Servicio | URL en el host |
|---|---|
| Frontend | http://localhost:8000 |
| API | http://localhost:9080 (también vía http://localhost:8000/api) |
| PostgreSQL | localhost:5433 |

El compose levanta su propio Postgres (servicio `db`) y crea el usuario con `DB_USER` y
`DB_PASSWORD` del `.env`, así que con el compose esas variables tienen que ser las de una base
local, no las de Supabase. Los puertos se cambian en `.env` y quedan ligados a `127.0.0.1`.

```bash
docker compose logs -f api        # ver logs de la API
docker compose up --build -d api  # reconstruir solo la API
docker compose down               # apagar (los datos se conservan)
docker compose down -v            # apagar y borrar la base
```

## Sincronización con GeoPagos

Las consultas de la API (deudas, contribuyentes, padrones) **solo leen la base propia**: nunca
llaman a GeoPagos. Los datos se cargan con la sincronización, que descarga todo, lo procesa en
memoria y lo guarda en **una sola transacción** (si algo falla no queda nada a medias).

### Cómo ejecutarla

Manual, con un usuario Administrador (por defecto `admin` / `admin123`, cambiarla antes de usar
datos reales):

```powershell
$api = "http://localhost:8080"
$login = Invoke-RestMethod -Method Post -Uri "$api/api/auth/login" -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin123"}'
Invoke-RestMethod -Method Post -Uri "$api/api/sincronizaciones/deudas" `
  -Headers @{ Authorization = "Bearer $($login.token)" } -TimeoutSec 600
```

Automática: `SINCRONIZACION_AUTOMATICA=true` en el `.env` (por defecto todos los días a las 3:00;
otro horario con `SINCRONIZACION_CRON`, formato cron de Spring, por ejemplo `0 0 3 * * *`).

Devuelve un resumen: deudas recibidas, creadas, actualizadas, canceladas, omitidas y hasta 100
advertencias con el motivo de cada omisión (solo CM, sin datos personales).

| Respuesta | Significa |
|---|---|
| 401 | Token ausente o vencido: repetir el login |
| 403 | El usuario no es Administrador |
| 409 | Ya hay una sincronización en curso |
| 502 | GeoPagos falló o no hay credenciales; el mensaje indica cuál (ej. "GeoPagos respondió HTTP 504") |

### Qué carga de cada endpoint de GeoPagos

| Endpoint | Uso |
|---|---|
| `/facturas/pendientes` | Contribuyente (nombre y documento), padrón, deuda (importe, vencimientos, años, convenio, tributos) y contactos de origen `API_FACTURAS_PENDIENTES` (teléfono, correo, domicilio) |
| `/facturas/canceladas` | Último cobro de cada CM, desde la sincronización anterior (90 días la primera vez) |
| `/contribuyentes-geopagos` | Contactos de origen `API_PERSONAS` (`esDeContribuyente=true`) y `API_GEOPAGOS` (`esDeContribuyente=false`: pueden ser de quien pagó). **Opcional**: si falla, las deudas se guardan igual y queda una advertencia |

### Reglas

- Solo se importan los CM con `IMPORTE_DEUDA > 0`. Con importe cero o negativo no se crea nada,
  y si la deuda ya existía se cancela. Un importe nulo se omite sin cancelar la deuda.
- Una deuda que ya no aparece en `/facturas/pendientes` pasa a `CANCELADA`.
- Estado de las deudas pendientes: con convenio activo → `EN_CONVENIO`; si estaba `CANCELADA` o
  `EN_CONVENIO` y ya no → `PENDIENTE`; `EN_GESTION` (lo pone un usuario) no se pisa. Son reglas
  provisorias hasta que la Intendencia confirme el CU 2.17.
- El segmento de mora se asigna según los días desde `DEUDA_DESDE` (vencimiento impago más
  antiguo) y los rangos configurados.
- El nombre y el documento del contribuyente salen de `/facturas/pendientes`, que los trae
  juntos. De `/contribuyentes-geopagos` solo se toma el nombre si faltaba.
- El documento identifica a la persona: un cambio de nombre no lo elimina. Si falta en una
  factura pendiente, se conserva el existente; si llega otro documento, se vincula el padrón
  a otro contribuyente sin cambiar la identidad del anterior.
- Un cobro histórico puede completar un documento ausente, pero no reemplaza uno existente.
  Si trae un documento diferente, conserva los datos del titular actual y registra el cobro.
- Si GeoPagos devuelve 0 deudas habiendo deudas activas, no se aplica ningún cambio (protege de
  una respuesta vacía por error del origen).
- La sincronizacion de contactos solo agrega datos faltantes por contribuyente, tipo y fuente.
  No sobrescribe ni elimina contactos existentes, incluidos los importados que se hayan corregido
  manualmente. Los valores vacios de la API se ignoran; no se crean contactos vacios.

### Tiempos medidos (08/10/2026, datos reales)

GeoPagos: `/facturas/pendientes` ~18 s (22.585 filas, 9 MB), `/contribuyentes-geopagos` ~15 s
(71.846 filas, 21 MB). Sincronización completa contra base local: ~40 s.

Para que sea rápida contra una base remota, las tablas que llena (`contribuyentes`, `padrones`,
`deuda`, `tributos`, `contactos`) usan secuencias que avanzan **de a 1000**: Hibernate reserva
bloques de IDs y envía los INSERT en lotes de 500 (`reWriteBatchedInserts=true`). Con IDs
`IDENTITY` cada fila es un viaje a la base (contra Supabase serían horas). Los huecos en los IDs
son normales; no volver esas entidades a `GenerationType.IDENTITY`.

## Modelo: padrón, CM y contribuyente

- El **CM** identifica al padrón en GeoPagos y es único (`padrones.cm`).
- El **número de padrón no es único**: cada localidad numera sus padrones por separado (en los
  datos reales, 3.164 números se repiten en distintas localidades).
- Cada deuda referencia un padrón, y cada padrón un contribuyente (`padrones.contribuyente_id`).
  GeoPagos no tiene un identificador de persona, así que no se agrupan padrones de una misma
  persona.
- `DOCUMENTO` solo viene en las facturas: puede quedar vacío si el padrón no aparece en ellas.

## Endpoints principales

| Endpoint | Descripción |
|---|---|
| `GET /api/deudas` | Paginado. Filtros: `estado`, `padron`, `contribuyente`, `localidad`, `segmento`, `tributo`, `conContacto`. Por defecto, deudas no canceladas con importe > 0, las más antiguas primero |
| `GET /api/deudas/agrupadas` | Cartera agrupada por documento; mismos filtros y paginación por contribuyentes |
| `GET /api/deudas/{id}`, `/api/deudas/{id}/tributos` | Detalle y tributos de una deuda |
| `GET /api/contribuyentes` | Paginado (`page`, `size`, `sort`; por defecto por nombre). Contribuyentes con deuda vigente, un registro por padrón. Filtros opcionales `nombre` y `documento` (coincidencia parcial, sin distinguir mayúsculas; si se envían ambos deben coincidir en el mismo contribuyente) |
| `GET /api/contribuyentes/{cm}` | Contribuyente del padrón con ese CM (404 si no existe) |
| `GET /api/contribuyentes/{cm}/padrones` | Todos los padrones vinculados al contribuyente de ese CM |
| `GET /api/padrones/{numeroPadron}` | **Lista** de padrones con ese número (uno por localidad). Filtro opcional `?localidad=` |
| `GET /api/padrones/{numeroPadron}/contribuyente` | Lista de contribuyentes de esos padrones |
| `POST /api/sincronizaciones/deudas` | Sincronización manual (Administrador) |

Las respuestas paginadas tienen la forma `{contenido, pagina, tamanio, totalElementos, totalPaginas}`
(máximo 100 por página). La documentación completa está en `/swagger-ui.html`.

## Migraciones (Flyway)

Las tablas las crea Flyway desde `src/main/resources/db/migration` (Hibernate solo valida).
Nombrar las migraciones nuevas con fecha y hora, `VAAAAMMDDHHMM__descripcion.sql`, para no chocar
con las de otros integrantes. Nunca modificar una migración que ya se aplicó en una base compartida:
crear una nueva.

## Windows: "bind: An attempt was made to access a socket in a way forbidden"

Windows reserva rangos de puertos para Hyper-V/WSL (ver con
`netsh interface ipv4 show excludedportrange protocol=tcp`). Si un puerto cae en esos rangos,
cambiarlo en `.env`, o liberar las reservas con una consola de administrador:
`net stop winnat` y luego `net start winnat`.


## Gestiones de una deuda

Desde la cartera, abrir un contribuyente y seleccionar la pestaña **Gestiones**. Se abre
la deuda de la fila seleccionada; cuando hay varias se puede cambiar con el selector de padrón/CM.
Cada deuda admite múltiples gestiones y su historial se ordena de más reciente a más antiguo.

| Endpoint | Uso |
| --- | --- |
| `GET /api/gestiones/deuda/{deudaId}` | Historial exclusivo de una deuda; 404 si no existe |
| `GET /api/gestiones/deuda/{deudaId}/acciones-disponibles` | Acciones activas asignadas a su segmento; lista vacía si no tiene segmento |
| `POST /api/gestiones` | Registrar una gestión; devuelve 201 |

Ejemplo de cuerpo de registro:

```json
{
  "deudaId": 123,
  "accionId": 5,
  "resultado": "RESPONDIO",
  "observacion": "Respondió al contacto."
}
```

- Resultados: `EXITOSA`, `NO_LOCALIZADO`, `RECHAZADA`, `PENDIENTE`, `RESPONDIO`, `NO_RESPONDIO`, `PROXIMO_A_VENIR`.
- Observación opcional, máximo 255 caracteres. Deuda y acción deben existir; no se admiten acciones inactivas.
- Fecha y autor se obtienen del servidor y del usuario autenticado. El campo anterior `fecha` se acepta
  por compatibilidad, pero no modifica la fecha registrada. No se admite un autor elegido por el cliente.
- Registrar no cambia automáticamente el estado de la deuda.
- Solo NOTIFICADOR registra gestiones. ADMINISTRADOR, NOTIFICADOR y ANALISTA consultan su historial.
  ADMINISTRADOR puede consultar toda la cartera sin necesidad de sumar otro rol.
- La migración `V202610121930__autor_gestiones.sql` incorpora el autor y un índice para el historial.
  Flyway la aplica al iniciar el backend actualizado; las gestiones anteriores conservan autor desconocido.

### Pruebas de gestiones

Los tests `GestionServiceTest` y `GestionControllerTest` verifican registro, autor, fecha,
resultados, validaciones, errores y consulta por deuda sin consultar la API externa.
`GestionPersistenciaTest` prueba también PostgreSQL, las migraciones, varias gestiones por deuda,
el aislamiento entre deudas y los contadores. Solo se activa al definir `GESTIONES_TEST_DB_URL`
con la URL JDBC de una base PostgreSQL desechable separada (usuario local `morosidad`, contraseña
local `morosidad-local`); los datos de cada prueba se revierten mediante transacción.

```powershell
mvn test '-Dtest=GestionServiceTest,GestionControllerTest'
# Para la prueba PostgreSQL, crear primero una base desechable:
docker exec morosidad-local createdb -U morosidad morosidad_gestiones_test
$env:GESTIONES_TEST_DB_URL = 'jdbc:postgresql://localhost:5434/morosidad_gestiones_test?sslmode=disable'
mvn test '-Dtest=GestionPersistenciaTest'
Remove-Item Env:GESTIONES_TEST_DB_URL
docker exec morosidad-local dropdb -U morosidad morosidad_gestiones_test
```


### Alcance inicial de acciones y segmentos

Según los resaltados amarillos de la política simplificada UTEC y los límites confirmados:

| Segmento | Días de atraso | Acciones del alcance inicial |
| --- | --- | --- |
| Mora Temprana | 0–90 inclusive | Recordatorios por WhatsApp; promoción de medios de pago online y débito automático |
| Mora Inicial | 91–365 inclusive | Intimaciones formales; convenios de refinanciación; contacto telefónico o personalizado |
| Mora Tardía | Desde 366 | Clearing de Informes; derivación a ejecución judicial |

Las acciones resaltadas en amarillo indican el alcance previsto para esta fase. Por decisión del
proyecto, se conserva el catálogo existente y sus estados, sin desactivar las otras acciones.
La acción de refinanciación permite registrar una gestión; no implementa la creación ni la
integración externa de convenios.

### Edicion de contactos desde la cartera

ADMINISTRADOR, NOTIFICADOR y ANALISTA pueden consultar, crear y editar contactos (GET/PUT) en `/api/contribuyentes/{cm}/contactos`, indicando su origen.
El PUT sin `id` crea un contacto; con `id` modifica solo un contacto del contribuyente
vinculado al CM. Tipo, valor, origen y pertenencia al contribuyente son obligatorios;
valor y origen admiten hasta 255 caracteres y se guardan sin espacios en los extremos.
Los contactos pertenecen al contribuyente y se comparten entre sus padrones.
No se necesita una migracion adicional para esta pantalla.

## Usuarios con multiples roles

En Configuracion > Usuarios, un administrador puede listar cuentas, crear usuarios,
activar/desactivar cuentas y asignar uno o varios roles existentes: ADMINISTRADOR,
NOTIFICADOR y ANALISTA. Los permisos se suman: NOTIFICADOR + ADMINISTRADOR puede
administrar acciones y usuarios. Los tres roles pueden consultar, agregar y editar contactos, indicando su origen.
No se implementan permisos individuales ni creacion de roles nuevos en esta etapa.
El registro de gestiones requiere NOTIFICADOR; ANALISTA solo consulta el historial.

Endpoints (requieren ADMINISTRADOR):

- `GET /api/usuarios`
- `GET /api/usuarios/{id}`
- `POST /api/usuarios` (201; antes la ruta de alta exigia un ID que no utilizaba)
- `PUT /api/usuarios/{id}`

Ejemplo de alta:

```json
{"username":"operador","password":"clave-inicial","roles":["NOTIFICADOR","ADMINISTRADOR"]}
```

Para modificar una cuenta se envian `username`, `roles` y `activo`; la contrasena se
conserva. Cada cuenta exige al menos un rol. La pantalla mantiene el nombre de usuario
sin cambios al editar y permite modificar roles y estado.

Login devuelve `roles` como lista. El JWT tambien incluye todos los roles y cada peticion
consulta los roles y estado actuales en la base: un token ya emitido no conserva permisos
revocados, y una cuenta desactivada pierde acceso. `GET /api/auth/me` permite actualizar
la informacion de sesion en el frontend.

La migracion `V202610130100__usuarios_multiples_roles.sql` crea `usuario_roles`, copia
el rol de cada usuario existente y reemplaza la columna singular. Se aplica con Flyway
al reiniciar el backend; conserva usuarios, contrasenas y sus roles previos.

`UsuarioPersistenciaTest` verifica la migracion con cuentas existentes, alta/login con
varios roles, permisos combinados, revocacion con el mismo token, cuentas inactivas,
reactivacion, roles obligatorios y duplicados. Se ejecuta exclusivamente con una base
desechable configurada mediante `USUARIOS_TEST_DB_URL`.

## Configuracion de tributos

Tipos de Tributo muestra el catalogo real con codigo y descripcion. Los tres roles pueden
consultarlo. Administrador (incluido un usuario con otros roles adicionales) puede:

- Agregar un tributo con codigo unico (hasta 20 caracteres) y descripcion (hasta 200).
- Completar o editar la descripcion de un tributo existente, conservando su codigo.
- Eliminar un tributo que no este asociado a ninguna deuda.

Endpoints: `GET /api/tributos`, `POST /api/tributos` (201),
`PUT /api/tributos/{id}` y `DELETE /api/tributos/{id}` (204).
Codigos duplicados devuelven 409. Tributos inexistentes devuelven 404. Eliminar un tributo
asociado a deudas devuelve 400 con un mensaje explicativo y conserva sus referencias.

No requiere una migracion adicional. La sincronizacion mantiene las descripciones
existentes; si la API trae un codigo eliminado, puede volver a incorporarlo al catalogo.
`TributoAdministracionTest` usa exclusivamente una base desechable indicada mediante
`TRIBUTOS_TEST_DB_URL` y comprueba permisos, persistencia y proteccion de las deudas.


## Configuracion de periodos de mora por tributo

Los rangos existentes son la base: Mora Temprana 0-90 dias, Mora Inicial 91-365 y Mora Tardia
366 en adelante. Cada tributo puede tener sus propios tres rangos. Si no tiene configuracion
propia, hereda la base. Solo Administrador puede personalizar o restaurar rangos; los otros
roles pueden consultar.

Una deuda puede incluir varios tributos: se calcula el segmento de cada uno con sus rangos
propios o la base, y se usa el mas avanzado (Tardia > Inicial > Temprana). La misma regla
se aplica durante la sincronizacion y al recalcular las deudas no canceladas.

- `GET /api/configuraciones-segmento`: consulta la base.
- `GET /api/configuraciones-segmento?tributoId={id}`: consulta rangos propios o hereda la base.
- `GET /api/configuraciones-segmento?tributoId={id}&historial=true`: historial propio del tributo.
- `PUT /api/configuraciones-segmento`: recibe `tributoId` y `rangos` con `segmentoId`,
  `diasDesde` y `diasHasta`. Guarda todos los rangos del tributo en una transaccion.
- `DELETE /api/configuraciones-segmento/tributo/{id}`: desactiva sus rangos propios y vuelve
  a usar la base, conservando su historial y recalculando las deudas.

Omitir `tributoId` en PUT conserva la compatibilidad con la configuracion base anterior.
El POST individual anterior solo modifica la base. La pantalla muestra la base como
referencia y edita los limites del tributo seleccionado.

Los rangos deben comenzar en cero, ser consecutivos y cubrir todos los segmentos una sola
vez. El ultimo no tiene limite superior. Cambios invalidos no modifican los rangos vigentes.
La migracion `V202610131200__periodos_por_tributo.sql` agrega la relacion opcional con
tributos y reemplaza el indice unico por indices de base y de tributo. Conserva los rangos
existentes como base. Se aplica al reiniciar el backend.

Los tributos con historial de configuracion no se eliminan para conservar ese historial.
`PeriodosMoraPersistenciaTest` requiere una base desechable en `PERIODOS_TEST_DB_URL`.
Verifica aislamiento por tributo, herencia, historial, restauracion, permisos y clasificacion
con varios tributos. Tambien se verifica la regla en `SincronizacionDeudasProcessorTest`.

## Permisos actuales

Los roles suman permisos. Administrador tiene acceso de consulta a todas las secciones:

- ADMINISTRADOR: administra usuarios, acciones, tributos y periodos; cambia estados de deuda
  y ejecuta sincronizacion manual. Consulta cartera, contribuyentes, padrones, contactos e historiales
  de gestiones. También puede agregar y editar contactos indicando su origen.
  No registra gestiones por tener solo este rol: registrar requiere NOTIFICADOR.
- NOTIFICADOR: consulta cartera e historial, registra gestiones y consulta/edita contactos.
- ANALISTA: consulta cartera e historial y consulta/edita contactos; no registra gestiones.
- ADMINISTRADOR + NOTIFICADOR: suma administracion y operacion completa.
- ADMINISTRADOR + ANALISTA: suma administracion y consulta/edicion de contactos; no registra gestiones.

Las restricciones se aplican en los controladores del backend y en la navegacion del frontend.
`PermisosRolesTest` comprueba los tres roles y sus combinaciones con Spring Security.
Este ajuste no requiere migracion de base de datos.

## Crear acciones con segmento de mora

En Configuracion > Acciones, Administrador puede crear una accion indicando nombre
(hasta 100 caracteres) y segmento de mora. `POST /api/acciones` recibe:

```json
{"nombre":"Contacto personalizado","segmentoId":1}
```

La accion se crea activa y su relacion en `acciones_segmentos` se guarda en la misma
transaccion. Se valida el segmento existente y el nombre unico sin distinguir mayusculas,
eliminando espacios al inicio y al final. Un nombre duplicado devuelve 409 y un segmento
inexistente 404. El segmento es obligatorio. No requiere una migracion adicional.

`GET /api/acciones` y las respuestas de alta/cambio de estado incluyen `segmentos`,
para mostrar las moras asignadas en el catalogo. Las acciones disponibles para registrar
una gestion siguen filtrandose por el segmento de la deuda y por su estado activo.

`GestionPersistenciaTest` verifica el alta, su vinculo persistido, el listado por segmento,
el registro de una gestion con la nueva accion y las validaciones sin registros parciales.


### Cartera agrupada por contribuyente

`GET /api/deudas/agrupadas` reúne las deudas por documento (conservando ceros iniciales
 e ignorando espacios exteriores). Dos nombres iguales con documentos distintos permanecen
separados. Si falta el documento, la clave usa el ID del contribuyente; nunca el nombre.
La agrupación es de consulta y no modifica entidades ni fusiona contactos o historiales.

Admite los mismos filtros de `/api/deudas`: `estado`, `padron`, `contribuyente` (nombre,
documento o CM), `localidad`, `segmento`, `tributo` y `conContacto`. Los filtros se aplican **antes** de sumar:
los importes y las deudas desplegables corresponden a los resultados filtrados. Por defecto
se excluyen canceladas y saldos no positivos, como en la cartera individual.

`page` y `size` paginan grupos completos en SQL. Cada grupo devuelve `clave`, `documento`,
`nombres`, `importeTotal`, `diasAtrasoMaximos`, `segmentoMora` (el más avanzado) y `deudas`
con sus IDs originales, CM, padrón, tributos, acciones sugeridas y contador de gestiones.
`sort=importe,desc` ordena por la suma del grupo; `deudaDesde,asc` por su deuda más antigua;
`padron.contribuyente.nombre,asc` por nombre. Se añade un desempate por clave para paginar.
Administrador, Notificador y Analista pueden consultar, igual que `/api/deudas`.

`CarteraAgrupadaPersistenciaTest` se ejecuta únicamente con `CARTERA_TEST_DB_URL` apuntando
a una base PostgreSQL desechable. Verifica identidad por documento, homónimos, ausencia
de documento, paginación completa, orden, filtros, saldos, permisos e historiales por deuda.


### Filtro de contactos en cartera

`GET /api/deudas` y `GET /api/deudas/agrupadas` admiten `conContacto=true` (con contacto)
y `conContacto=false` (sin contacto). Si se omite, se incluyen ambos casos.
Se considera contacto cualquier correo, teléfono o domicilio con valor no vacío asociado
al contribuyente del padrón, cualquiera sea su origen y aunque corresponda a un tercero.
Los valores compuestos solo de espacios, tabulaciones o saltos de línea no cuentan.

El filtro se ejecuta mediante EXISTS antes de paginar/agrupar; varios contactos no duplican
deudas ni importes. Se combina con los demás filtros y se incluye en el total de resultados.
Cada `DeudaResponse` devuelve además `conContacto`, calculado con los contactos guardados.
No se cambia el esquema ni se requiere una migración.

Las pruebas de `CarteraAgrupadaPersistenciaTest` cubren ambos modos, tipos de contacto,
contactos de terceros, valores vacíos, paginación, sumas y un contacto recién registrado.
