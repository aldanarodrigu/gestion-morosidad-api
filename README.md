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
| `ADMIN_BOOTSTRAP_PASSWORD` | En prod con base vacía | Contraseña del primer administrador. Al menos 15 caracteres y hasta 72 bytes en UTF-8 |
| `JWT_EXPIRATION_SECONDS` | No | Duración del token en segundos (por defecto 3600 = 1 hora). Al vencer, el frontend vuelve al login |
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

## Cambio y restablecimiento de contraseña

El menú de la cuenta permite cambiar la contraseña desde cualquier rol. La API identifica al usuario
a partir del token y exige la contraseña actual.

- `PUT /api/auth/me/password` recibe `passwordActual` y `passwordNueva`. Devuelve 204 y
  revoca los tokens anteriores de esa cuenta. La contraseña nueva admite al menos 15 caracteres
  y hasta 72 bytes en UTF-8. Luego se vuelve a iniciar sesión.
- `POST /api/usuarios/{id}/password/reset` requiere Administrador y solo permite operar sobre
  otra cuenta. Revoca sus tokens y entrega una contraseña temporal que vence en 24 horas.
  Tras ingresar con esa clave, solo quedan habilitadas la consulta de sesión y el cambio de contraseña.
  El reset conserva los roles y el estado activo de la cuenta.

En **Configuración → Usuarios**, la contraseña temporal aparece una vez para entregarla por un
canal seguro. El servidor guarda su hash y los logs identifican las operaciones por ID de usuario.

Para el primer arranque con el perfil `prod` y una base vacía, definí `ADMIN_BOOTSTRAP_PASSWORD`.
Después de crear la cuenta inicial, podés retirar la variable. Si reutilizás una base donde la cuenta
local conserva su contraseña de prueba, cambiala antes de exponer el sistema.

## Sincronización con GeoPagos

Las consultas de la API (deudas, contribuyentes, padrones) **solo leen la base propia**: nunca
llaman a GeoPagos. Los datos se cargan con la sincronización, que descarga todo, lo procesa en
memoria y lo guarda en **una sola transacción** (si algo falla no queda nada a medias).

### Cómo ejecutarla

Usá un usuario con rol Administrador. En desarrollo, `admin` / `admin123` es la cuenta local
de prueba que se crea si la tabla de usuarios está vacía y no se configuró una clave inicial.
En producción usá la contraseña configurada o la de tu cuenta existente:

```powershell
$api = "http://127.0.0.1:9080" # Docker; si se ejecuta Java directamente, usar su puerto configurado.
$cred = Get-Credential -UserName "admin" -Message "Credenciales de Gestión de Morosidad"
$login = Invoke-RestMethod -Method Post -Uri "$api/api/auth/login" -ContentType "application/json" `
  -Body (@{ username = $cred.UserName; password = $cred.GetNetworkCredential().Password } | ConvertTo-Json -Compress)
Invoke-RestMethod -Method Post -Uri "$api/api/sincronizaciones/deudas?origen=INTENDENCIA" `
  -Headers @{ Authorization = "Bearer $($login.token)" } -TimeoutSec 3600
# Para GeoPat: reemplazar INTENDENCIA por SUCIVE.
```

Automática: habilitada por defecto todos los días a las 3:00 de Uruguay. En `.env`,
`SINCRONIZACION_AUTOMATICA=false` la deshabilita y `SINCRONIZACION_CRON` cambia el horario
(formato cron de Spring, por ejemplo `0 0 3 * * *`). Docker Compose transmite ambas opciones
al backend; al cambiarlas se debe recrear el contenedor de la API.

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
- Si aparece su CM pero falta el numero de padron, la fila se omite conservando la deuda
  existente (importe, estado y fecha de sincronizacion). Un dato incompleto no demuestra
  que haya sido saldada. Las deudas de CM realmente ausentes se siguen conciliando.
- Inicio, fin y fechas guardadas usan el mismo reloj de `America/Montevideo`, incluso
  cuando el contenedor se ejecuta en UTC.
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
  una respuesta vacía por error del origen). Con período configurado, la protección se aplica si
  hay deudas no canceladas con `DEUDA_DESDE` dentro del período.
- Con período, una fila con saldo cero o negativo es una omisión válida (cancela la deuda
  existente). Una fila incompleta (sin padrón o importe nulo) o un CM repetido rechazan toda la
  respuesta sin aplicar cambios.
- La sincronizacion de contactos solo agrega datos faltantes por contribuyente, tipo y fuente.
  No sobrescribe ni elimina contactos existentes, incluidos los importados que se hayan corregido
  manualmente. Los valores vacios de la API se ignoran; no se crean contactos vacios, y un padrón
  sin dato no descarta el que trae otro padrón del mismo contribuyente.

### Verificacion de sincronizacion

Revision de sincronizacion (20/10/2026): se agregaron 15 casos de prueba para padrones
incompletos, reloj de Uruguay con servidor UTC, paginacion de los tres endpoints GeoPagos,
fallos de cobros obligatorios y preservacion en PostgreSQL de las deudas de ambos origenes.
Ocho casos reprodujeron fallos antes de corregirlos. Tras las correcciones, la suite completa
del backend ejecuto 299 pruebas sin fallos ni omitidas, usando exclusivamente PostgreSQL
desechable. Tambien se verificaron los valores predeterminados y personalizados de la
configuracion automatica en Docker Compose.

Pruebas del procesador (17/11/2026):

- `SincronizacionDeudasProcessorReglasTest` (unitaria, sin base): estados del CU 2.17,
  protección ante respuestas vacías, CM repetidos o numéricos, tributos, límites de los
  segmentos, cobros e identidad del contribuyente.
- `SincronizacionCasosRealesPersistenciaTest` (PostgreSQL desechable, solo con
  `SINCRONIZACION_TEST_DB_URL`): dos días de sincronización con respuestas que tienen la forma
  real de GeoPagos (`src/test/resources/sincronizacion/casos-reales`, datos ficticios) y el
  flujo del servicio con período. Encontraron y quedaron corregidos tres fallos: contactos de
  un padrón vacío que descartaban los de otro padrón de la misma persona, falta de protección
  ante respuestas vacías con período, y rechazo de toda la sincronización con período cuando
  una fila traía saldo cero.

```bash
docker exec morosidad-local createdb -U morosidad sincronizacion_test
SINCRONIZACION_TEST_DB_URL=jdbc:postgresql://localhost:5434/sincronizacion_test \
  ./mvnw test -Dtest=SincronizacionCasosRealesPersistenciaTest
```

### Tiempos medidos (08/10/2026, datos reales)

GeoPagos: `/facturas/pendientes` ~18 s (22.585 filas, 9 MB), `/contribuyentes-geopagos` ~15 s
(71.846 filas, 21 MB). Sincronización completa contra base local: ~40 s.

Para que sea rápida contra una base remota, las tablas que llena (`contribuyentes`, `padrones`,
`deuda`, `tributos`, `contactos`) usan secuencias que avanzan **de a 1000**: Hibernate reserva
bloques de IDs y envía los INSERT en lotes de 500 (`reWriteBatchedInserts=true`). Con IDs
`IDENTITY` cada fila es un viaje a la base (contra Supabase serían horas). Los huecos en los IDs
son normales; no volver esas entidades a `GenerationType.IDENTITY`.

## Modelo: padrón, CM y contribuyente

- El **CM** identifica al padrón dentro de cada proveedor. La clave única es `(origen, cm)`;
  un mismo CM de GeoPagos y GeoPat puede representar padrones diferentes.
- El **número de padrón no es único**: cada localidad numera sus padrones por separado (en los
  datos reales, 3.164 números se repiten en distintas localidades).
- Cada deuda referencia un padrón, y cada padrón un contribuyente (`padrones.contribuyente_id`).
  La sincronización vincula contribuyentes por documento cuando está disponible; sin documento
  conserva la persona del padrón o crea una propia. No une personas solamente por su nombre.
- `DOCUMENTO` solo viene en las facturas: puede quedar vacío si el padrón no aparece en ellas.

## Endpoints principales

| Endpoint | Descripción |
|---|---|
| `GET /api/deudas` | Paginado. Filtros: `estado`, `padron`, `tipoPadron`, `origen`, `contribuyente`, `localidad`, `segmento`, `tributo`, `conContacto`. Por defecto, deudas no canceladas con importe > 0, las más antiguas primero |
| `GET /api/deudas/agrupadas` | Cartera agrupada por documento; mismos filtros y paginación por contribuyentes |
| `GET /api/deudas/exportar` | Excel `.xlsx` de toda la cartera filtrada; mismos filtros y orden. `agrupar=true` agrega un resumen por contribuyente |
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

PostgreSQL no indexa las claves foráneas: al agregar una FK que se use en filtros o joins, crear
también su índice. `V202611171919__indices_claves_foraneas.sql` agrega los de
`contactos.contribuyente_id`, `padrones.contribuyente_id` y `gestiones.accion_id`; sin el primero,
la cartera filtrada "con contacto" y por localidad tardaba unos 20 s por página.

## Rendimiento

`RendimientoPersistenciaTest` genera datos sintéticos con el volumen real de GeoPagos (22.600 deudas,
71.800 contribuyentes) y mide, con el pool de producción (3 conexiones), la sincronización (primera
carga, diaria y sin cambios) y las consultas paginadas principales (p50, p95 y máximo de 10
repeticiones). Falla si una sincronización supera 2 minutos, una página de cartera 1 s (p95), el
reporte 5 s o la exportación a Excel 15 s, y escribe el informe en `target/rendimiento/informe.md`.
No mide la descarga desde GeoPagos, que depende de la red: esa se toma del resumen de la
sincronización en QA. Necesita una base vacía en cada corrida (tarda unos 3 minutos):

```bash
docker exec morosidad-local dropdb -U morosidad --if-exists rendimiento_test
docker exec morosidad-local createdb -U morosidad rendimiento_test
RENDIMIENTO_TEST_DB_URL=jdbc:postgresql://localhost:5434/rendimiento_test \
  ./mvnw test -Dtest=RendimientoPersistenciaTest
```

Medido el 17/11/2026: primera carga 10,7 s, sincronización diaria 6 s, páginas de cartera entre
5 y 400 ms (p95), reporte 0,3 s y exportación a Excel 6,5 s.

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
- Observación opcional, máximo 255 caracteres. Deuda y acción deben existir; no se admiten acciones inactivas. La acción debe estar vinculada al segmento de mora actual de la deuda; sin segmento o fuera del período de gestión se rechaza el registro.
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

El nombre de usuario es unico sin distinguir mayusculas ni espacios al inicio y al final
(`Admin` y `admin` no pueden coexistir); un duplicado devuelve 409. El login sigue usando
el nombre exacto guardado.

El sistema nunca queda sin administrador: un administrador no puede quitarse el rol
`ADMINISTRADOR` ni desactivar su propia cuenta, y no se puede quitar el rol ni desactivar
al ultimo administrador activo. Ambos casos devuelven 400 sin modificar la cuenta.

Login devuelve `roles` como lista. El JWT tambien incluye todos los roles y cada peticion
consulta los roles y estado actuales en la base: un token ya emitido no conserva permisos
revocados, y una cuenta desactivada pierde acceso. `GET /api/auth/me` permite actualizar
la informacion de sesion en el frontend.

La migracion `V202610130100__usuarios_multiples_roles.sql` crea `usuario_roles`, copia
el rol de cada usuario existente y reemplaza la columna singular. Se aplica con Flyway
al reiniciar el backend; conserva usuarios, contrasenas y sus roles previos.

`UsuarioPersistenciaTest` verifica la migracion con cuentas existentes, alta/login con
varios roles, permisos combinados, revocacion con el mismo token, cuentas inactivas,
reactivacion, roles obligatorios, duplicados sin distinguir mayusculas y la proteccion del
ultimo administrador. Se ejecuta exclusivamente con una base
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


## Configuracion de periodos de mora por PadTipo

Los rangos base son Mora Temprana 0-90 dias, Mora Inicial 91-365 y Mora Tardia
366 en adelante. Cada PadTipo (CIU, CIR, FAC, etc.) puede tener sus propios rangos.
Una deuda usa los periodos del tipo de su padron, independientemente de cuantos
tributos tenga o cuales sean. Si el tipo no tiene configuracion propia, hereda la base.
La misma regla se aplica durante la sincronizacion y al recalcular segmentos.

Solo Administrador puede personalizar o restaurar periodos. La pantalla muestra la
base como referencia y permite editar el PadTipo seleccionado. Guardar o restaurar
recalcula las deudas no canceladas de ese tipo; los otros tipos conservan sus rangos.
Cambiar la base por API recalcula todas las deudas no canceladas.

- `GET /api/configuraciones-segmento/tipos-padron`: tipos de todos los padrones locales,
  incluyendo tipos con configuraciones anteriores y sin deuda activa.
- `GET /api/configuraciones-segmento`: rangos base.
- `GET /api/configuraciones-segmento?tipoPadron=CIU`: rangos propios o heredados.
- `GET /api/configuraciones-segmento?tipoPadron=CIU&historial=true`: historial propio.
- `PUT /api/configuraciones-segmento`: recibe `tipoPadron` y `rangos` con
  `segmentoId`, `diasDesde` y `diasHasta`. Guarda el conjunto en una transaccion.
- `DELETE /api/configuraciones-segmento/tipo-padron/CIU`: vuelve a la base y conserva
  los rangos propios como historial.

Los rangos deben empezar en cero, ser consecutivos, cubrir todos los segmentos una
sola vez y dejar el ultimo sin limite superior. No se aceptan cambios invalidos ni
PadTipo inexistentes. El antiguo `tributoId` en PUT se rechaza para evitar que un
cliente anterior modifique accidentalmente la base. Omitir `tipoPadron` en PUT o
usar POST individual conserva la API de configuracion base.

La migracion `V202610161400__periodos_mora_por_padtipo.sql` agrega `tipo_padron` e
indices unicos por tipo y segmento. Conserva la base y archiva cualquier configuracion
anterior por tributo: no existe una correspondencia univoca para trasladarla a un tipo.
Los registros historicos y su referencia al tributo se conservan. Al migrar, las deudas
no canceladas se reclasifican con la base; luego se pueden personalizar los PadTipo.

`PeriodosMoraPersistenciaTest` usa exclusivamente PostgreSQL desechable mediante
`PERIODOS_TEST_DB_URL`. Verifica aislamiento por PadTipo, herencia, historial,
restauracion, permisos y deudas del mismo tipo con diferentes combinaciones de tributos.
`SincronizacionDeudasProcessorTest` verifica la misma asignacion al importar datos.

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
`MatrizPermisosTest` recorre todos los endpoints con las 7 combinaciones de roles y sin sesión, y
falla si se agrega un endpoint sin declarar quién puede usarlo: al cambiar permisos, actualizar su
tabla `MATRIZ`. `PermisosRolesTest` cubre casos puntuales (por ejemplo, que el servicio de gestiones
no se invoque si se rechaza el acceso) y `AutenticacionJwtTest` valida los tokens: vencidos, con otra
firma, alterados o sin firma, y revocación de roles o desactivación con un token ya emitido.
`POST /api/auth/login` ignora el header `Authorization`, para que un token vencido guardado en el
navegador no impida volver a iniciar sesión.
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

`DELETE /api/acciones/{id}` elimina la accion junto con sus vinculos a segmentos (204). Si ya
tiene gestiones registradas devuelve 400 y no borra nada: el historial se conserva y la accion
se desactiva con `PUT /api/acciones/{id}` (`{"activo":false}`). `AccionAdministracionTest`
cubre alta, duplicados (409), inexistentes (404), validaciones (400) y la baja, contra una base
desechable indicada en `ACCIONES_TEST_DB_URL`. No usa `@Transactional` para que las claves
foraneas se verifiquen al confirmar, como en produccion.


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

## Integracion GeoPat (origen SUCIVE)

Los tres endpoints de cada proveedor se descargan completos con `page` y `per_page`.
`UTEC_API_PAGE_SIZE` configura el tamaño solicitado (1 a 1000; predeterminado 1000),
tambien dentro de Docker Compose. Los filtros de fecha de cobros se conservan en cada pagina.
Antes de conciliar se validan total estable, numero y tamaño de pagina, ultima pagina,
`has_more`, limites `from`/`to`, cantidad de filas y CM sin repeticiones en pendientes y
contribuyentes. Una pagina fallida o inconsistente en pendientes o cobros aborta sin escribir.
Si falla una pagina de contribuyentes se descarta esa consulta opcional completa.
Las paginas se consultan fuera de la transaccion y el avance se registra cada diez paginas.
La primera carga puede tardar varios minutos; no reintentar mientras otra siga en curso.
Las fechas `0000-00-00` recibidas del origen se interpretan como no informadas (`null`).
La deuda positiva se conserva; si falta su fecha inicial no se calculan dias ni segmento
de mora. No se sustituyen fechas ni se aceptan otros valores invalidos silenciosamente.

Validacion de la primera carga local completa (20/10/2026): se recibieron 119.660 filas
de pendientes y 10.451 de cobros, ademas de 161.256 registros de contribuyentes. Se crearon
118.895 deudas SUCIVE de saldo positivo y se omitieron 765 filas segun las validaciones
de importacion. Quedaron 74 deudas sin fecha inicial informada. La cartera individual y
agrupada devolvieron los registros importados; las 22.966 deudas de Intendencia conservaron
sus importes, estados y fechas de sincronizacion, verificados antes y despues de la carga.

La cartera comparte la pantalla para los dos proveedores. El origen viene de `padron.origen`:
`INTENDENCIA` para GeoPagos y `SUCIVE` para GeoPat. El filtro `origen` se aplica en SQL en
la cartera individual y agrupada, incluyendo sus totales y paginacion.

- `POST /api/sincronizaciones/deudas`: conserva la sincronizacion de GeoPagos.
- `POST /api/sincronizaciones/deudas?origen=SUCIVE`: sincroniza GeoPat (Administrador).
- `GET /api/deudas?origen=SUCIVE` y `GET /api/deudas/agrupadas?origen=SUCIVE`: cartera GeoPat.
- Las consultas por CM de contribuyentes, contactos y deudas aceptan `?origen=SUCIVE`;
  omitirlo conserva Intendencia. Las consultas por ID interno son univocas entre origenes.

SUCIVE utiliza la misma URL base y Basic Auth, con las rutas `/api/v1/utec/sucive/contribuyentes`,
`/api/v1/utec/sucive/facturas/pendientes` y `/api/v1/utec/sucive/facturas/canceladas` de la documentación vigente.
Los DTO de facturas se comparten porque los campos y formatos documentados coinciden;
el cliente GeoPat normaliza contribuyentes sin inventar los campos de transacciones GeoPagos.

La migracion `V202610161800__origen_geopat.sql` conserva los registros existentes como
Intendencia. Los padrones son unicos por `(origen, cm)` y los tributos por `(origen, codigo)`:
no se presume que identificadores iguales de bases distintas representen lo mismo. Los
contribuyentes con el mismo documento pueden compartir persona; los contactos importados
conservan fuentes separadas (`API_GEOPAT_PERSONAS`, `API_GEOPAT_PENDIENTES`).

Cada sincronizacion obtiene su propio intervalo de cobros y solo actualiza o cancela deudas
de ese origen. Una respuesta invalida o un fallo en pendientes/canceladas no escribe cambios.
La proteccion contra pendientes vacias tambien se limita al origen consultado. Solo se crean
padrones con deuda vencida positiva; nombres, documentos y contactos nulos son admisibles.
Los periodos de mora siguen dependiendo del PadTipo del padron, con herencia de rangos base.

Si la sincronizacion automatica esta habilitada, ejecuta ambos proveedores de forma independiente:
el fallo de uno no impide intentar el otro. El bloqueo de ejecuciones simultaneas sigue siendo
por instancia del backend. La consulta opcional de contribuyentes puede fallar sin perder las
facturas validas; sus contactos se completan en otra sincronizacion.

Pendiente de validar con clientes: gestion especifica de CNV/convenios. El anexo advierte que
la deuda de un convenio puede aparecer en su padron y en los padrones vinculados; no permite
deducir esas relaciones para corregir un total general. Se conservan los importes y codigos
informados, sin excluir CNV ni aplicar deduplicaciones supuestas.

Pendiente de consultar al proveedor: datos personales de GeoPat. El 20/10/2026 se revisaron
1.000 filas de pendientes y 1.000 de contribuyentes, mas consultas por CM de CNV, GEN, PAT
y VAR. En las muestras, `PERSONA`, `DOCUMENTO` y `NOMBRE_PERSONAS` llegaron vacios; el
endpoint de contribuyentes tampoco incluyo un campo de documento. Los 118.895 padrones
SUCIVE importados quedaron sin nombre ni documento. Se requiere que el origen informe
estos datos o indique la consulta que permite obtenerlos; no se infieren identidades por
nombre, matricula ni CM de otro origen.

Pendiente: activar el filtro «Con multa / Sin multa» incluyendo multas de transito SUCIVE
y multas/recargos de Intendencia, segun lo confirmado por el usuario. El selector actual
solo mantiene el estado en la interfaz/URL; no aplica un predicado en la API. Las respuestas
de pendientes revisadas no incluyen importes ni indicadores separados de multa/recargo,
y los tributos importados carecen de descripcion. Se requiere un catalogo por origen que
confirme si esos conceptos se incluyen en TRIBUTOS_DEUDA, o campos explicitos del proveedor.
Una deuda vencida o un origen SUCIVE no bastan para clasificarla. Al implementarlo, el filtro
debe aplicarse antes de paginar/agrupar y conservarse en los totales y la exportacion.

`GeoPatClientTest` verifica las rutas, fechas y formatos del anexo sin red. `GeoPatPersistenciaTest`
usa exclusivamente PostgreSQL desechable indicado por `GEOPAT_TEST_DB_URL`; prueba CM y
tributos repetidos entre origenes, cancelaciones independientes, datos personales nulos,
filtros, contactos, agrupacion y el endpoint de sincronizacion.


### Exportación de cartera a Excel

El panel permite elegir **Excel (.xlsx)** (predeterminado) o **CSV (.csv)**. Ambos formatos
usan los mismos filtros, columnas y todas las páginas. El CSV es una sola tabla de detalle,
también cuando se agrupa; el resumen por contribuyente en una segunda hoja es exclusivo de XLSX.
CSV usa UTF-8 con BOM, separador `;`, comillas escapadas y protección contra fórmulas.
Para conservar tipos y ceros iniciales al abrir directamente en Excel, usar XLSX.
El endpoint acepta `formato=XLSX|CSV`; omitirlo mantiene el comportamiento XLSX anterior.

El botón **Exportar Excel** abre un panel con filtros opcionales de **segmento de mora**,
**PadTipo** y **origen**. Se puede elegir uno, combinar varios o dejarlos todos vacíos.
Estos filtros son independientes de los filtros del listado y comienzan vacíos.
**Descargar Excel** genera `cartera-morosidad.xlsx` con todas las filas que cumplen la
combinación elegida, independientemente de la página visible. Sin filtros, exporta toda
la cartera vigente (excluye canceladas e importes no positivos, igual que el listado).
**Limpiar filtros** restablece la exportación completa. Se conserva el orden y la opción
de agrupar por contribuyente de la cartera.
Consulta únicamente la base local: no dispara sincronizaciones ni llamadas a GeoPagos/GeoPat.

- Hoja **Cartera**: CM, N.º padrón, contribuyente, documento, origen, PadTipo, tributo,
  segmento, días vencido, deuda, cantidad de gestiones y todas las acciones sugeridas.
  También conserva convenio y disponibilidad de contacto.
- Con **Agrupar por contribuyente** activado, agrega la hoja **Contribuyentes** con
  documento, nombre, cantidad de deudas, segmento más avanzado, días máximos de atraso y
  deuda total; la hoja Cartera conserva el detalle completo de esas deudas.
- CM, número de padrón y documento se guardan como texto para conservar ceros iniciales.
  Deuda, días y cantidad de gestiones son celdas numéricas.
- Solo los usuarios con rol Analista pueden exportar, también cuando tienen otros roles. Se requiere autenticación.
- Endpoint: `GET /api/deudas/exportar`. Acepta los mismos filtros y `sort` del listado,
  además de `agrupar=true|false`. `page` y `size` no limitan la exportación.
- La lectura interna se realiza por lotes dentro de una misma transacción de solo lectura
  para mantener resultados consistentes si hay una sincronización concurrente.

Pruebas específicas: `CarteraExportacionServiceTest` valida el archivo XLSX, todas las
páginas, agrupación y tipos de celdas; `CarteraExportacionControllerTest` valida filtros,
descarga y permisos. En el frontend, `CarteraExportacion.test.ts` y `carteraService.test.ts`
comprueban solicitud, descarga y errores.


### Consulta de facturas por padrón

En el detalle del contribuyente, pestaña **Padrones**, **Ver facturas** despliega una
consulta directa al proveedor correspondiente. No sincroniza, no guarda facturas y no
modifica el saldo ni la deuda seleccionada para las gestiones.

- `GET /api/deudas/{id}/facturas`: consulta autorizada para Administrador, Notificador y Analista.
  El ID es el de la deuda local; el servidor obtiene su CM y origen, sin aceptar otro proveedor desde el navegador.
- Filtros opcionales: `estado`, `vencida` y `incluirConvenios` (por defecto `true`).
- GeoPagos: `/api/v1/utec/facturas`; SUCIVE: `/api/v1/utec/sucive/facturas`.
  Estas consultas no usan paginación.
- Se conservan los identificadores como texto y los montos con sus decimales originales.
  Se muestran monedas, códigos de unidad desconocidos y montos por unidad sin sumarlos.
- Puede incluir facturas pagadas, pendientes no vencidas y convenios históricos. No debe
  interpretarse el total de esas facturas como el saldo pendiente sincronizado de la cartera.
- Abrir el panel no cambia el padrón que se está gestionando. Cerrar cancela la petición del navegador.


### Capturas reales de CM seleccionados

**Comparar cartera** utiliza capturas persistentes en la base local. **Nueva captura**
permite asignar un nombre y agregar uno o varios pares origen–CM existentes. Al guardar
se copia el estado sincronizado, sin llamar a la API externa ni disparar una sincronización.

- Migración `V202611031900__capturas_cartera.sql`: tablas `capturas_cartera` y
  `captura_cuentas`. La primera guarda nombre, fecha/hora y autor autenticado; la segunda
  guarda los datos del padrón y contribuyente, saldo pendiente, estado, segmento,
  fecha de sincronización y último cobro informado.
- Los datos históricos no tienen referencias a la deuda o padrón actual que permitan
  sobrescribirlos al sincronizar. La captura completa se guarda en una transacción;
  si un CM no existe o está repetido para el mismo origen, no se guarda parcialmente.
- Se admiten cuentas sin deuda pendiente: se conserva el CM con saldo cero.
  Eso no equivale a una confirmación de pago.
- No hay un límite de cantidad de capturas. Cada operación acepta hasta 100 CM.
- Administrador, Notificador y Analista pueden crear y consultar capturas.
- Endpoints: `GET /api/capturas-cartera`, `GET /api/capturas-cartera/{id}` y
  `POST /api/capturas-cartera` con `{nombre, cuentas: [{cm, origen}]}`.
- La comparación solo utiliza cuentas incluidas en ambas capturas y que cumplen los
  filtros. Una cuenta no incluida no se interpreta como pagada ni desaparecida.
- Se compara el saldo agregado por CM, estado y segmento. No se conserva el detalle de
  facturas ni un historial completo de cobros; el último cobro puede ser anterior a las
  capturas y no representa la recaudación del período.
- La fecha de creación es independiente de la última sincronización. Dos capturas sin
  cambios de los datos locales pueden mostrar exactamente los mismos valores.
- Reiniciar el backend aplica la migración mediante Flyway. Las capturas se conservan
  tras reiniciar el frontend o backend; requieren conservar el volumen de PostgreSQL.

`CapturaPersistenciaTest` se ejecuta solo con `CAPTURA_TEST_DB_URL`,
`CAPTURA_TEST_DB_USER` y `CAPTURA_TEST_DB_PASSWORD` apuntando a una base PostgreSQL
**desechable y aislada**, nunca a la base de desarrollo. Comprueba la migración,
persistencia, conservación de valores históricos y rechazo de selecciones inválidas.


### Reportes reales de cartera

La pestaña **Reportes** consulta toda la cartera vigente en la base local: deudas no
canceladas con saldo positivo, igual que el listado de cartera. No utiliza datos de
prueba ni carga una página para calcular los totales en el navegador.

- `GET /api/reportes/cartera`: indicadores de deuda total, contribuyentes distintos,
  padrones y deudas; distribución por segmento y PadTipo; desglose por origen y PadTipo.
- Filtros opcionales y combinables: `origen=INTENDENCIA|SUCIVE`, `tipoPadron` y
  `segmento` (ID real del catálogo). Sin filtros se incluyen todos los registros vigentes.
- Los contribuyentes se identifican por documento con espacios exteriores eliminados;
  sin documento se usa su ID local. El total general cuenta personas distintas: no
  necesariamente equivale a sumar los contribuyentes de cada categoría.
- Los padrones sin tipo y deudas sin segmento se conservan en sus categorías respectivas.
- Todos los cálculos se ejecutan en SQL dentro de una transacción de lectura consistente,
  incluyendo durante una sincronización concurrente.
- `GET /api/reportes/cartera/exportar`: descarga `reporte-cartera.xlsx` con los mismos
  filtros, resumen, distribución por mora, por PadTipo, por origen y desglose por origen/PadTipo.
  Importes, cantidades y porcentajes se guardan como valores numéricos.
- Administrador, Notificador y Analista tienen acceso a consulta. La exportación requiere Analista, solo o combinado con otros roles. Criterio pendiente de validación con el cliente.
- No requiere nuevas tablas ni migraciones. No llama a GeoPagos ni a SUCIVE.
- La fecha de generación no es una fecha de sincronización. Para actualizar los datos
  externos se debe ejecutar la sincronización correspondiente.
- Evolución mensual, recaudación completa y clasificación urbano/rural siguen pendientes
  de los datos y definiciones correspondientes. No se calculan a partir del último cobro.

`ReportePersistenciaTest` prueba totales, filtros, valores faltantes y XLSX contra una
base PostgreSQL desechable configurada con `REPORTE_TEST_DB_URL`,
`REPORTE_TEST_DB_USER` y `REPORTE_TEST_DB_PASSWORD`. Nunca apuntar estas variables
al entorno de desarrollo o producción. `ReporteControllerTest` cubre permisos.


### Catálogo paginado de tipos de tributo

Configuración → Tipos de Tributo muestra 20 registros por página, con buscador por código
(o parte del código) y descripción, filtro de origen y total de resultados. La búsqueda se
realiza en el backend sobre el catálogo completo, sin limitarse a la página visible.

- `GET /api/tributos/paginados?page=0&size=20&busqueda=...&origen=SUCIVE` devuelve
  el formato `PaginaResponse` con contenido y totales. Los filtros son opcionales;
  búsqueda por código/descripción sin distinguir mayúsculas y con espacios exteriores eliminados.
- Mantiene un orden estable por origen, longitud del código, código e ID. Los códigos
  se conservan como texto y no pierden ceros iniciales.
- La búsqueda trata `%`, `_` y `!` como caracteres literales, no como comodines SQL.
- `GET /api/tributos` sigue devolviendo el catálogo completo para consumidores existentes.
- Los cambios de filtros vuelven a la primera página. Tras crear, editar o eliminar,
  se recarga la página y sus totales; si desaparece la última página, se vuelve a una válida.
- Crear/editar/eliminar conserva los permisos anteriores y el diálogo de confirmación.
- No requiere una migración ni cambios de datos existentes.


### Consulta de convenios pendientes

La pestaña **Convenios** consulta directamente las fuentes externas mediante `GET /api/convenios` (autenticación requerida; Administrador, Notificador y Analista).

Parámetros opcionales: `origen=INTENDENCIA|SUCIVE` (Intendencia por defecto), `pagina=1`, `tamanio=20` (máximo 100), `cm`, `documento`, `nombre`, `desde` y `hasta` (AAAA-MM-DD). Las páginas comienzan en 1. `desde` incluye el vencimiento y `hasta` lo excluye; seleccionan deuda actualmente pendiente, no una cartera histórica. La respuesta conserva `data` y `meta` de la página externa.

Incluye número de convenio, CM, contribuyente, cuotas vencidas/totales, matrícula cuando existe y desglose de deuda, saldo a favor y crédito descontado. Los importes se conservan como texto decimal de la fuente, sin convertir ni asumir moneda. Las cuotas vencidas excluyen la entrega inicial; el importe puede incluirla.

Esta primera versión es de consulta: no importa convenios a la base local, no registra gestiones de convenios y no suma estos saldos a cartera/reportes, evitando duplicarlos con registros existentes. No requiere migración. Los errores externos se comunican y permiten reintentar desde la pantalla.


### Motivo del último movimiento

La sincronización de ambos orígenes recibe `MOTIVO_ANULACION` de facturas canceladas y guarda `motivoUltimoCobro` junto a fecha e importe. Solo el valor `PAGO` identifica el pago informado por la fuente; otros textos son motivos de anulación y un valor ausente permanece sin informar. El motivo no se usa para deducir la cancelación de la deuda: la conciliación sigue basándose en la consulta completa de pendientes.

La respuesta de deudas expone el motivo y las nuevas capturas lo copian para conservar su valor histórico. Comparar cartera muestra **Último movimiento informado** y su motivo. Las capturas anteriores no se reconstruyen ni se atribuyen automáticamente a pagos.

La migración `V202611112100__motivo_ultimo_cobro.sql` agrega columnas opcionales a deuda y captura_cuentas; Flyway la aplica al iniciar el backend. Los datos anteriores quedan sin motivo hasta que corresponda actualizarlos mediante sincronización (las capturas anteriores permanecen sin modificar). La sincronización consulta un intervalo de cobros, no reconstruye todo el historial.


### Desglose del saldo por padrón

La sincronización recibe `DEUDA_FACTURAS`, `SALDO_A_FAVOR` y `CREDITO_DESCONTADO` de ambas fuentes y los guarda como `deudaFacturas`, `saldoAFavor` y `creditoDescontado`. `importe` sigue siendo la deuda neta informada en `IMPORTE_DEUDA`: no se recalcula ni se descuenta el crédito nuevamente. Un campo ausente permanece null; cero es un valor informado.

En el detalle del contribuyente, pestaña **Padrones**, cada fila ofrece **Desglose del saldo**, con estos tres valores y la deuda neta, sin agregar columnas al listado de cartera. Los valores nuevos se muestran sin asumir una moneda.

Las nuevas capturas conservan el desglose de las deudas pendientes. Para cuentas sin deuda pendiente no se copia un desglose potencialmente antiguo. Las capturas y deudas existentes quedan con valores sin informar hasta que corresponda sincronizar (las capturas anteriores no se reconstruyen).

La migración aditiva `V202611112200__desglose_saldo_deuda.sql` incorpora columnas opcionales a deuda y captura_cuentas. Se aplica con Flyway al iniciar el backend; no borra datos.


### Matrícula de SUCIVE

La sincronización recibe `MATRICULA` de pendientes de SUCIVE y la guarda como texto opcional en el padrón. La consulta de contribuyentes de SUCIVE completa matrículas faltantes. Un valor ausente no borra una matrícula existente. No se usa como identificador: se conserva la separación por origen y CM.

La matrícula se expone en `padron.matricula`, se muestra junto al número de padrón en cartera y en la pestaña Padrones (también en el detalle agrupado), y se incluye como columna Matrícula en la hoja Cartera del Excel. El buscador general acepta matrícula, sin distinguir mayúsculas, desde el backend y sobre toda la cartera, incluida la agrupación y la paginación.

Flyway aplica `V202611112300__matricula_padron.sql` al iniciar el backend. Se necesita una sincronización para completar las matrículas de los padrones existentes.


### Personas vinculadas al padrón

En el detalle del contribuyente, pestaña **Padrones**, cada fila ofrece **Ver personas vinculadas**. El panel consulta el historial externo al abrirse y separa relaciones actuales (`ACTIVO`) de históricas (`NO ACTIVO - SOLO HISTORICO`). Conserva una fila por relación/padrón, aunque se repita el documento; para convenios puede incluir padrones de origen. Incluye nombre, documento, código de contribuyente, relación, situación y contactos disponibles actualmente en la fuente.

`GET /api/deudas/{id}/historial-contribuyentes` requiere autenticación y admite Administrador, Notificador y Analista. Resuelve el CM y el origen desde la deuda local y consulta `/api/v1/utec/contribuyentes/historial` o `/api/v1/utec/sucive/contribuyentes/historial`. Devuelve `data` y `meta.total`, sin paginación. Rechaza respuestas incompletas y comunica errores de la fuente; el panel permite reintentar y cancela consultas al cerrarse.

Es una consulta informativa: no modifica titulares, documentos, contactos ni gestiones locales. No requiere migración ni sincronización. Las relaciones no acreditan por sí solas titularidad u obligación de pago.


### Facturas y años de deuda

Pendientes de ambos orígenes incorpora `CANT_FACT_VENC`, `CANT_FACT_CON_DEUDA`, `ANIO_DESDE`, `ANIO_HASTA` y `CANT_ANIOS_DEUDA`. Se guardan/exponen como `cantidadFacturasVencidas`, `cantidadFacturasConDeuda`, `anioDesde`, `anioHasta` y `cantidadAniosDeuda`, junto al listado existente `aniosDeuda`.

En **Detalle del contribuyente → Padrones → Facturas y años de deuda** se muestran sin consultas externas adicionales. Las facturas vencidas consideradas pueden incluir importes cero, negativos o desconocidos; las facturas con deuda son las de importe positivo antes de aplicar el saldo a favor. Los años son de facturación, no necesariamente años de vencimiento; no se infiere que todos los años del intervalo tengan deuda. Un campo ausente queda sin informar, distinto de cero.

El Excel de cartera incluye estos cinco campos y la lista de años, tanto en exportación normal como agrupada. Las nuevas capturas copian los datos de las cuentas con deuda pendiente; las capturas anteriores no se reconstruyen. No se suman contadores a los reportes agregados.

La migración `V202611120000__facturas_anios_deuda.sql` agrega columnas opcionales a deuda/captura_cuentas. Se aplica con Flyway al iniciar; la siguiente sincronización completa las deudas existentes con los datos recibidos.


### Período de sincronización y ejecución manual

En **Configuración → Sincronización**, Administrador guarda el año mínimo (2013 inicialmente) e inicia la sincronización de Intendencia o SUCIVE. La configuración se persiste en la base y se utiliza tanto manualmente como en el proceso automático. Cambiar el año no cambia los saldos hasta completar una nueva sincronización de cada origen.

El período corresponde a **vencimientos** desde el 01/01 del año elegido, incluido, hasta el día actual, excluido (`fecha_inicio`/`fecha_corte`). No equivale a filtrar por año de facturación. Se recorren todas las páginas con `include_total=0` y `meta.has_more`, validando páginas, tamaños, límites e identificadores. El COUNT externo se omite para evitar su coste, y la importación solo se aplica una vez completa la descarga. Una respuesta con vencimientos fuera de los límites se rechaza.

Los CM ausentes en la consulta filtrada quedan con `enPeriodoGestion=false`, conservando identidad, importe anterior, estado y gestiones. No se interpreta su ausencia como pago ni cancelación. Cartera, agrupación, detalle habitual, reportes y exportaciones excluyen esos registros; siguen disponibles por ID para consulta histórica. Las nuevas capturas reflejan saldo cero y `FUERA_PERIODO` para una cuenta excluida. Si vuelve a aparecer al ampliar el período, se reactiva usando los datos de la fuente. `anioPeriodo` registra el año aplicado a cada deuda durante la última sincronización de su origen.

Endpoints de Administrador:

- `GET /api/sincronizaciones/configuracion`: año mínimo guardado.
- `PUT /api/sincronizaciones/configuracion`: JSON `{"anioMinimo":2013}` (1900 a año actual).
- `POST /api/sincronizaciones/ejecucion?origen=INTENDENCIA|SUCIVE`: inicia en segundo plano, HTTP 202.
- `GET /api/sincronizaciones/ejecucion`: estado de la última ejecución manual de esta instancia (204 si no hubo ninguna). El estado de seguimiento se reinicia al reiniciar el backend; los datos y configuración quedan persistidos.
- `POST /api/sincronizaciones/deudas?origen=...`: conserva la ejecución síncrona existente y usa el año guardado.

La pestaña permite consultar el avance aunque se cierre y vuelva a abrir. No inicia trabajos simultáneos ni permite modificar el período durante una ejecución. Cada origen se concilia en su propia transacción; un fallo conserva su base anterior y no demuestra que ya esté actualizado al nuevo año. No reintentar mientras figure en curso.

La migración `V202611121000__configuracion_periodo_sincronizacion.sql` crea la configuración y agrega campos de alcance sin borrar datos. No elimina facturas históricas de consultas informativas directas, convenios consultados en línea ni capturas anteriores.


La descarga reintenta una misma página hasta tres veces ante HTTP 502/503/504 o fallos de conexión, con una espera breve entre intentos. No reintenta errores de validación y no importa resultados parciales si se agotan los intentos.

Validación real del 11/11/2026: Intendencia quedó sincronizada desde 2013; SUCIVE no pudo completarse por 504 de la fuente en páginas avanzadas. Sus datos anteriores se conservaron. Ver [informe de validación](docs/sincronizacion-2013-validacion-2026-11-11.md).


### Representación del progreso de sincronización

Configuración → Sincronización muestra las etapas **Deudas → Cobros → Contactos → Guardado**, alimentadas por `progreso` en `GET /api/sincronizaciones/ejecucion`. El backend publica las páginas y registros únicamente después de validar cada página; mientras espera la siguiente informa `paginaActual`, y ante errores transitorios informa el número de intento (hasta tres), conservando los contadores validados.

Cuando la fuente informa totales, la pantalla calcula porcentaje y registros/páginas restantes **de esa descarga**, no de todo el proceso. Para pendientes sin COUNT, la barra es indeterminada y los totales permanecen null; no se inventan porcentajes ni tiempos de finalización. Los contadores se reinician al cambiar de consulta. Durante el guardado se espera la confirmación de la transacción antes de marcar la sincronización completada. Si falla, se detiene la animación y se conserva el último avance junto al mensaje de error.

El seguimiento se aísla por hilo y se libera al finalizar, para no mezclar tareas manuales con otras ejecuciones. Consultar el progreso no ejecuta importaciones ni modifica datos. No requiere migración ni altera el período configurado.


### Cancelar una sincronización manual

Administrador puede solicitar `POST /api/sincronizaciones/ejecucion/cancelar`, también disponible como **Cancelar sincronización** en la pestaña. La tarea pasa a `CANCELANDO` y conserva bloqueados el inicio y el cambio de año hasta que el trabajador termina. Si una petición externa sigue activa, se espera su respuesta o timeout; después se descarta lo descargado y se confirma `CANCELADA`, sin procesar ni guardar deudas. No se interrumpe el hilo ni se libera prematuramente el bloqueo.

La cancelación y el comienzo de **Guardado** se coordinan atómicamente. Una vez iniciada esa etapa, el botón se deshabilita y el backend rechaza cancelar; se deja finalizar la transacción para evitar anunciar una cancelación después de confirmar cambios. La cancelación es idempotente si la tarea ya terminó. No cancela trabajos automáticos ni los iniciados mediante el endpoint síncrono anterior.

### Sincronizacion sin anio minimo

En Configuracion > Sincronizacion se puede dejar el anio minimo vacio y guardar el periodo. El API acepta `{"anioMinimo":null}` (o el campo omitido) y persiste NULL, sin alterar el anio configurado al instalar la migracion `V202611161000__sincronizacion_sin_anio_minimo.sql`. Se aplica tanto a ejecuciones manuales como automaticas.

Sin limite se omite `fecha_inicio`, se conserva `fecha_corte` y se envia `include_total=0` en todas las paginas y reintentos de pendientes. Las deudas antiguas recibidas vuelven a estar incluidas en cartera. La descarga completa conserva la conciliacion existente de cuentas ausentes y la proteccion contra respuestas vacias inesperadas. Guardar el periodo no inicia una sincronizacion ni modifica deudas.

### Prueba de arranque aislada

`GestionMorosidadApiApplicationTests` requiere `APP_TEST_DB_URL` hacia una base PostgreSQL local cuyo nombre empiece por `app_test_`. Sin esa variable, se omite: ejecutar `mvn test` no debe arrancar la aplicación contra la base del `.env`. La prueba carga las propiedades generales sin importar `.env`, usa credenciales de prueba explícitas, una clave JWT ficticia y deshabilita la sincronización automática. Comprueba el arranque, las migraciones y la inicialización del administrador en esa base desechable.

```powershell
$nombrePrueba = 'app_test_' + [guid]::NewGuid().ToString('N')
docker exec morosidad-local createdb -U morosidad $nombrePrueba
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base desechable' }
try {
    $env:APP_TEST_DB_URL = "jdbc:postgresql://localhost:5434/$nombrePrueba"
    ./mvnw.cmd test '-Dtest=GestionMorosidadApiApplicationTests,ContribuyenteServiceTest,ContribuyenteConsultasControllerTest'
} finally {
    Remove-Item Env:APP_TEST_DB_URL -ErrorAction SilentlyContinue
    docker exec morosidad-local dropdb -U morosidad --force $nombrePrueba
}
```
