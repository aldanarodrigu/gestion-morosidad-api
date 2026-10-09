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
- Si GeoPagos devuelve 0 deudas habiendo deudas activas, no se aplica ningún cambio (protege de
  una respuesta vacía por error del origen).
- Los contactos de origen manual se conservan.

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
| `GET /api/deudas` | Paginado. Filtros: `estado`, `padron`, `contribuyente`, `localidad`, `segmento`. Por defecto, deudas no canceladas con importe > 0, las más antiguas primero |
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
