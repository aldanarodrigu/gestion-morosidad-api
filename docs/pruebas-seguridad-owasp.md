# Checklist de seguridad: OWASP API, datos personales y credenciales por defecto

Revisión del 25/11/2026 sobre `main` (commit `e27882b`), por lectura de código y configuración. Corresponde al módulo **Seguridad** del plan de testing (checklist manual; el escaneo con OWASP ZAP queda pendiente y se registra al final).

Estados: **OK** = cumple y tiene test o evidencia en el código · **HALLAZGO** = riesgo a corregir · **PENDIENTE** = falta verificarlo de forma manual o en un entorno real.

## Resumen de hallazgos

| # | Severidad | Hallazgo | Dónde |
|---|-----------|----------|-------|
| H1 | Media | **Corregido (25/11/2026).** El token se asociaba solo al `username` (`sub`), no al id. Si se renombra un usuario y otro toma ese nombre (o se crea uno nuevo con él), los tokens vigentes del primero autentican como el segundo, porque ambos tienen `version_credencial = 0`. | `TokenService`, `JwtConfig`, `UsuarioService.updateUser` |
| H2 | Media | **Corregido (25/11/2026).** Los usuarios que crea un administrador no cumplían la política de contraseñas: mínimo 8 caracteres en vez de 15 y sin cambio obligatorio en el primer ingreso. El administrador conocía la clave definitiva del usuario. | `UsuarioRequest`, `UsuarioService.saveUser` |
| H3 | Media | **Corregido (25/11/2026).** Swagger UI y `/v3/api-docs` eran públicos también con el perfil `prod`: exponían el mapa completo de la API sin autenticación. | `SecurityConfig`, `application-prod.properties` |
| H4 | Baja | **Corregido (25/11/2026).** El bloqueo de login era por cuenta: cualquiera podía bloquear la cuenta `admin` (u otra conocida) con 5 intentos fallidos. Tampoco había límite por IP, lo que permitía probar una misma clave contra muchas cuentas (password spraying). | `LimitadorLogin` |
| H5 | Baja | Fuera de `prod`, el admin inicial se crea con `admin/admin123` y no se le exige cambiar la clave. Si una base de QA o demo con datos reales se levanta sin el perfil `prod`, queda con una credencial conocida. | `DataInitializer` |
| H6 | Baja | Al cambiar los roles o desactivar un usuario no se incrementa `version_credencial`. Hoy no es explotable, porque los roles y `activo` se leen de la base en cada request, pero el claim `roles` del JWT queda desactualizado y no debe usarse para autorizar. | `UsuarioService.updateUser` |

Corrección sugerida:

- **H1 (corregido):** el token lleva el claim `uid` con el id del usuario y `JwtConfig` lo compara con la cuenta que hoy tiene ese username; si no coincide o falta, responde 401. Incrementar `version_credencial` al renombrar no alcanzaba, porque una cuenta nueva arranca en 0. Tests: `AutenticacionJwtTest#tokenDeUnaCuentaCuyoUsernameTomoOtraNoAutenticaComoLaNueva` y `#tokenSinIdDeUsuarioDevuelve401`.
- **H2 (corregido):** el alta aplica `PoliticaPassword.validarNueva` (15 caracteres, hasta 72 bytes) y deja la clave como temporal por 24 h, igual que el reset: al ingresar, el usuario solo puede cambiarla. Tests: `UsuarioServiceTest#elAltaDejaLaClaveComoTemporalYExigeCambiarlaAlIngresar`, `#claveQueNoCumpleLaPoliticaNoCreaElUsuario` y `UsuarioPersistenciaTest`.
- **H3 (corregido):** `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false` en `application-prod.properties`; en desarrollo siguen disponibles. Test: `DocumentacionApiPerfilTest` (200 sin perfil, 404 con `prod`).
- **H4 (corregido):** `LimitadorLogin` lleva tres contadores: cuenta+IP (5), IP contra cualquier cuenta (20, no se reinicia con un login correcto) y cuenta desde todas las IP (50). Cinco fallos ya no bloquean la cuenta para otras direcciones; bloquearla en todas exige 50 fallos desde al menos 10 IP. La IP del cliente sale de `X-Real-IP` detrás de nginx (verificado con la API levantada). Tests: `PasswordPersistenciaTest` (bloqueo solo desde esa IP, 20 fallos por IP, 50 por cuenta).
- **H5:** marcar la cuenta como temporal (`password_temporal_hasta`) también en dev.

## 1. OWASP API Security Top 10 (2023)

### API1 · Autorización a nivel de objeto (BOLA)
- [x] **OK**: todos los endpoints de datos exigen un rol (`@PreAuthorize` por clase o método). `MatrizPermisosTest` recorre todos los endpoints con las 7 combinaciones de roles y sin sesión, y falla si aparece un endpoint no declarado.
- [x] **OK**: no hay datos "propios" por usuario. Los tres roles ven toda la cartera por diseño, así que la autorización por objeto se reduce al rol.
- [x] **OK (H1 corregido)**: un token emitido para una cuenta no autentica como otra que después tomó su `username`.

### API2 · Autenticación rota
- [x] **OK**: JWT HS256 con algoritmo fijado en el decoder; los tokens sin firma o con otra firma se rechazan (`AutenticacionJwtTest`).
- [x] **OK**: el secreto sale de `JWT_SECRET` (Base64, ≥32 bytes), sin valor por defecto: sin él la API no arranca.
- [x] **OK**: el token dura 1 h por defecto (`JWT_EXPIRATION_SECONDS`).
- [x] **OK**: la revocación aplica a tokens ya emitidos. Roles y `activo` se leen de la base en cada request, y el claim `credencial` invalida los tokens después de un cambio o reset de clave.
- [x] **OK**: contraseñas con BCrypt; se rechazan las de más de 72 bytes para evitar el truncado silencioso.
- [x] **OK**: el login se bloquea tras 5 fallos en 15 min (configurable) y el contador está en base, así que vale entre instancias.
- [x] **OK**: el login responde "Credenciales inválidas" tanto si el usuario no existe como si la clave es incorrecta.
- [x] **OK**: el reset genera una clave temporal aleatoria de 24 bytes, válida 24 h y con cambio obligatorio; mientras tanto solo se permiten `/api/auth/me` y el cambio de clave.
- [x] **OK (H2 corregido)**: el alta de usuarios exige 15 caracteres y obliga a cambiar la clave en el primer ingreso.
- [x] **OK (H4 corregido)**: el bloqueo por cuenta es por IP, hay límite por IP contra cualquier cuenta y un tope por cuenta para ataques distribuidos.
- [ ] **PENDIENTE**: comparar con ZAP los tiempos de respuesta del login con usuario existente e inexistente (enumeración por timing).

### API3 · Autorización a nivel de propiedad (exposición y asignación masiva)
- [x] **OK**: las respuestas usan DTOs/records. `UsuarioResponse` no incluye el hash, la versión de credencial ni el vencimiento.
- [x] **OK**: `UsuarioUpdateRequest` no permite tocar la contraseña. `password` y `version_credencial` son `updatable=false` y solo cambian con la actualización condicional del repositorio.
- [x] **OK**: el campo `activo` de `UsuarioRequest` se ignora al crear (siempre `true`).
- [ ] **PENDIENTE**: revisar uno por uno los DTOs de contribuyente, padrón y contactos para confirmar que cada rol recibe solo los campos que necesita (ver sección 2).

### API4 · Consumo de recursos sin restricción
- [x] **OK**: la paginación está limitada a 100 (`spring.data.web.pageable.max-page-size`, `PaginaResponse`).
- [x] **OK**: la sincronización manual no admite ejecuciones simultáneas (`OperacionEnCurso` → 409).
- [ ] **PENDIENTE**: las exportaciones CSV (`/api/deudas/exportar`, `/api/cancelaciones/exportar`, `/api/reportes/cartera/exportar`) devuelven la cartera completa en memoria. Hay que medir el tiempo y la memoria con unas 22.000 deudas y ver qué pasa con varias exportaciones en paralelo, considerando el pool de 3 conexiones.
- [ ] **PENDIENTE**: definir un límite de tamaño del body y de requests por IP en nginx o en el proxy de producción.

### API5 · Autorización a nivel de función
- [x] **OK**: las funciones administrativas (usuarios, acciones, tributos, segmentos, periodos, estado de deuda, sincronización) son solo de `ADMINISTRADOR`.
- [x] **OK**: registrar gestiones es solo de `NOTIFICADOR`; el ADMINISTRADOR solo no puede (`MatrizPermisosTest`, `PermisosRolesTest`).
- [x] **OK**: las exportaciones son solo de `ANALISTA`.
- [x] **OK**: el sistema nunca queda sin administrador: nadie se quita el rol a sí mismo ni se da de baja al último admin activo.
- [x] **OK**: un administrador no puede resetear su propia clave por la vía de reset.

### API6 · Acceso sin restricción a flujos sensibles del negocio
- [x] **OK**: los cambios de estado de deuda y la sincronización están restringidos a ADMINISTRADOR, y `EN_GESTION` nunca se sobrescribe desde la sincronización.
- [ ] **PENDIENTE**: confirmar con la Intendencia si las exportaciones masivas de datos personales deben quedar auditadas (quién, cuándo, con qué filtros). Hoy no se registran.

### API7 · Server-Side Request Forgery
- [x] **OK**: la única llamada saliente es a GeoPagos/GeoPat, con una URL base fija por configuración. Ningún parámetro del usuario forma parte de la URL.

### API8 · Configuración de seguridad incorrecta
- [x] **OK**: el perfil `prod` no devuelve stacktrace, mensaje ni errores de binding, y el JSON malformado o la ruta inexistente responden sin detalles internos (`PerfilProdErroresTest`).
- [x] **OK**: Actuator expone solo `health`, sin detalles; `env`, `beans` y `heapdump` no están expuestos (ver `pruebas-despliegue-prod.md`).
- [x] **OK**: CSRF está deshabilitado de forma correcta para una API stateless con Bearer: no hay cookies de sesión.
- [x] **OK**: no hay CORS configurado, así que se aplica la política same-origin y el frontend entra por el proxy `/api` de nginx.
- [x] **OK**: los contenedores corren sin root, con el backend `read_only`, `no-new-privileges` y puertos publicados solo en 127.0.0.1.
- [x] **OK (H3 corregido)**: con el perfil `prod`, Swagger UI y OpenAPI no se publican (404).
- [ ] **PENDIENTE**: TLS y HSTS en el dominio real; las pruebas locales fueron por HTTP.
- [ ] **PENDIENTE**: ejecutar el baseline scan de ZAP contra el Compose local y revisar las cabeceras de la API (`X-Content-Type-Options`, `Cache-Control: no-store` en las respuestas con datos personales).

### API9 · Gestión inadecuada del inventario
- [x] **OK**: no hay versiones viejas de endpoints; el README documenta los endpoints y `MatrizPermisosTest` detecta los que no están declarados.
- [ ] **PENDIENTE**: confirmar que la base compartida de Supabase (desarrollo) no contiene datos reales de producción, o tratarla como producción.

### API10 · Consumo inseguro de APIs externas
- [x] **OK**: las credenciales de GeoPagos se envían por Basic Auth desde variables de entorno y no se escriben en logs.
- [x] **OK**: hay timeouts de conexión y lectura configurados, con reintentos acotados (2).
- [x] **OK**: una respuesta vacía con deudas activas no aplica cambios, y todo se procesa en una sola transacción con rollback (`SincronizacionRollbackPersistenciaTest`).
- [x] **OK**: los textos que vienen de GeoPagos y se exportan a CSV se neutralizan contra inyección de fórmulas (`Csv.linea` antepone `'` a `= + - @`).
- [ ] **PENDIENTE**: verificar que `GEOPAGOS_BASE_URL` use HTTPS con validación de certificado en producción. El default ya es `https://`.

## 2. Datos personales (Ley 18.331)

Datos tratados: documento, nombre, dirección y padrones del contribuyente; teléfonos y emails de contacto; deudas, gestiones e historial de titulares.

- [x] **OK**: el acceso está limitado a usuarios autenticados con rol; no hay endpoints públicos con datos personales.
- [x] **OK**: los logs de aplicación registran ids y conteos, no documentos, nombres ni contactos (revisados todos los `log.*` de `src/main`). `show-sql=false` en prod.
- [x] **OK**: los errores en prod no devuelven datos de la entidad ni SQL.
- [x] **OK**: la respuesta del reset de clave lleva `Cache-Control: no-store`.
- [ ] **PENDIENTE**: agregar `Cache-Control: no-store` a las respuestas de contribuyentes, contactos y exportaciones, para que no queden en la caché del navegador ni de proxies.
- [ ] **PENDIENTE**: minimización por rol. Hoy los tres roles ven y editan contactos. Hay que confirmar con la Intendencia que el ANALISTA necesita editarlos (README: "consulta/edita contactos").
- [ ] **PENDIENTE**: trazabilidad. No queda registro de quién consultó o exportó datos de un contribuyente. Las gestiones sí guardan su autor.
- [ ] **PENDIENTE**: retención. No hay una política de borrado o anonimización de contribuyentes sin deuda ni de contactos viejos. Hay que definirla con la Intendencia.
- [ ] **PENDIENTE**: los CSV exportados contienen datos personales y salen del sistema. Hay que documentar el procedimiento de manejo de esos archivos.
- [ ] **PENDIENTE**: confirmar el cifrado en tránsito hacia la base: `DB_SSLMODE=require` con Supabase, mientras que el default `prefer` admite conexiones sin TLS.
- [ ] **PENDIENTE**: confirmar que los backups de la base, si existen, estén cifrados y con acceso restringido.

## 3. Credenciales por defecto y secretos

- [x] **OK**: en `prod` no hay contraseña por defecto para el admin. Sin `ADMIN_BOOTSTRAP_PASSWORD` (mínimo 15 caracteres) la API no arranca en una base vacía (`DataInitializerTest`).
- [x] **OK**: el admin inicial se crea solo si la tabla `usuarios` está vacía; renombrarlo o darlo de baja no lo recrea con la clave conocida.
- [x] **OK**: `JWT_SECRET` y `DB_PASSWORD` son obligatorios en Compose (`:?`) y `JWT_SECRET` no tiene default en `application.properties`.
- [x] **OK**: `.env` está en `.gitignore`, `.env.example` no tiene valores reales y los contextos de build excluyen `.env*` y `.git`.
- [x] **OK**: en la base, `DB_PASSWORD` tiene un default vacío, no una clave conocida.
- [ ] **HALLAZGO H5** (`admin123` fuera de prod, sin cambio obligatorio).
- [ ] **PENDIENTE**: revisar el historial de git en busca de secretos commiteados (`git log -p | grep -iE "password|secret|jwt"` o gitleaks).
- [ ] **PENDIENTE**: en el despliegue real, confirmar que el perfil activo sea `prod` (el Dockerfile lo fija) y que nadie lo sobrescriba con `SPRING_PROFILES_ACTIVE`.
- [ ] **PENDIENTE**: rotación. Hay que documentar cómo se rota `JWT_SECRET` (invalida todas las sesiones) y las credenciales de GeoPagos y de la base.

## 4. Pruebas manuales a ejecutar (con ZAP o curl)

1. Login con `admin/admin123` contra el entorno de QA levantado con el perfil `prod`: debe fallar.
2. Token vencido, token con otra firma y token con `alg: none` contra `/api/deudas`: deben dar 401.
3. Con un token de NOTIFICADOR, llamar a `/api/usuarios`, `/api/deudas/exportar` y `PUT /api/deudas/{id}/estado`: deben dar 403.
4. Verificar H1: renombrar el usuario `a` a `a2`, crear un usuario nuevo `a` y usar el token viejo de `a`: debe dar 401.
5. Verificar H4: con 5 logins fallidos contra `admin` desde una IP, el admin real sigue entrando desde otra; con 20 fallos contra cuentas distintas, esa IP recibe 429.
6. Con el perfil prod: `GET /swagger-ui.html` y `/v3/api-docs` sin token: deben responder 404 (H3).
7. Baseline scan de ZAP contra el Compose local: revisar cabeceras, cookies y divulgación de información.
8. Exportación CSV con un contribuyente cuyo nombre empiece con `=`: la celda debe salir con `'` al inicio.
