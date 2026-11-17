# Pruebas de despliegue y perfil prod

Validación local del 17/11/2026: se construyeron las imágenes desde los Dockerfiles del backend y frontend y se levantó el Compose completo en un proyecto temporal independiente. No se usó la base de trabajo ni sus credenciales. La sincronización automática se deshabilitó y no se consultó GeoPagos/GeoPat.

## Pruebas automatizadas

### Backend

```powershell
./mvnw.cmd test '-Dtest=PerfilProdErroresTest,DeudaControllerTest,GestionControllerTest,ContribuyenteContactosControllerTest,SegmentoControllersTest'
```

59 pruebas aprobadas, sin omitidas. Incluyen 5 casos con el perfil prod activo: error interno 500 sin stacktrace, clases Java ni conexión SQL; validación 400; JSON malformado sin detalles del parser; ruta inexistente 404; y acceso sin sesión 401. La sonda que provoca el error solo existe en código de test y no se incluye en la imagen de ejecución.

Se corrigió el manejador global: `NoResourceFoundException` y `NoHandlerFoundException` ahora responden 404 con un mensaje público genérico, en lugar del 500 genérico.

### Docker Compose

Desde la raíz del backend, con Docker Desktop y el repositorio del frontend al lado:

```powershell
# Cuatro checks de configuración; no levanta contenedores.
python scripts/test_despliegue_prod.py

# Construye y prueba los tres servicios en un proyecto desechable.
python scripts/test_despliegue_prod.py --runtime
```

El script solo requiere Python estándar y Docker Compose. No lee el `.env` de trabajo: genera uno temporal con claves aleatorias, puertos libres en localhost y credenciales externas vacías. El proyecto usa un nombre aleatorio `morosidad-prod-qa-*`, con red y volumen propios. En el cierre elimina únicamente los contenedores, red, volumen y archivo temporal de ese proyecto; las capas de imágenes pueden quedar en la caché local de Docker.

Pasaron los 4 checks de configuración y los 9 casos de ejecución:

- JWT_SECRET y DB_PASSWORD obligatorios para Compose.
- Puertos publicados solo en 127.0.0.1, backend read-only, /tmp temporal y no-new-privileges.
- Contextos de build sin `.env`, variantes `.env.*` ni `.git`.
- Backend con perfil prod y procesos de API/nginx ejecutándose sin root.
- Health disponible, sin componentes, conexión de base ni detalles internos; se admiten los nombres públicos de grupos liveness/readiness.
- Endpoints Actuator de configuración, beans y heapdump no expuestos.
- API protegida, token inválido rechazado y login incorrecto sin información interna.
- Frontend y rutas SPA servidos con cabeceras de seguridad; proxy /api funcionando con login y consulta autenticada.
- Migraciones aplicadas y observación de conexiones JDBC dentro del límite predeterminado de 3. No sustituye una prueba de carga o concurrencia.
- Reinicio del backend conservando el año configurado y sin repetir versiones en el historial Flyway.

## Alcance y configuración pendiente antes de un uso real

La comprobación de Docker es local, por HTTP y con datos ficticios. No valida un dominio público, TLS, copias de seguridad, restauración operativa ni rendimiento bajo carga.

La inicialización actual crea `admin` con la contraseña conocida `admin123`. La prueba la utiliza exclusivamente para entrar en su base nueva de QA. Ese comportamiento no se cambió: para un despliegue con usuarios reales se debe cambiar esa contraseña o revisar el aprovisionamiento inicial con el equipo de autenticación. El resultado de estas pruebas no implica que esa clave sea adecuada para producción.
