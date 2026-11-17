# Cobertura funcional y unitaria adicional

## Controladores

Las clases `CarteraAgrupadaControllerTest`, `PadronControllerTest`, `SincronizacionControllerTest` y `UsuarioControllerTest` usan `@WebMvcTest` con la configuración real de seguridad y los servicios simulados. No requieren PostgreSQL ni llaman a la Intendencia.

- Cartera agrupada: filtros transferidos al servicio, parámetros inválidos (400), paginación y límite máximo de 100 grupos, normalización de página/tamaño, estructura de grupos y acceso anónimo.
- Padrones: respuesta con origen/CM/documento/matrícula, localidad en consulta de titular, padrón o contribuyente inexistente (404), acceso anónimo.
- Sincronización: proveedor predeterminado y SUCIVE, respuesta de resultados, origen inválido (400), operación simultánea (409), error externo (502) sin stacktrace.
- Usuarios: creación con tres roles (201), respuesta sin contraseña, validaciones del cuerpo y del identificador (400), inexistente (404), duplicado (409) y usuario de sesión al actualizar.

Los códigos no aplicables a un endpoint no se inventan: por ejemplo, listar una página vacía de cartera no devuelve 404 y sincronizar no tiene paginación HTTP.

## Servicios sin base de datos

`UsuarioServiceTest`, `ContactoServiceTest`, `AuthServiceTest`, `CarteraAgrupadaServiceTest` y `SegmentoMoraServiceTest` ejecutan la lógica real con Mockito y comprueban resultados y efectos:

- Usuarios: normalización, contraseña codificada, múltiples roles, duplicados, usuario inexistente, protección del último administrador y de la propia cuenta, contraseña conservada al actualizar.
- Contactos: aislamiento por proveedor, creación/edición, pertenencia al contribuyente, CM inexistente y listado vacío.
- Autenticación: credenciales entregadas al AuthenticationManager antes de emitir JWT, roles completos, credenciales incorrectas, cuenta deshabilitada y usuario ausente sin emitir token.
- Agrupación: metadatos de página vacía, suma exacta, mora/días máximos, grupo completo, orden de grupos y separación de identidades sin documento.
- Segmentos: normalización, duplicados, persistencia del orden y listado vacío/ordenado.

Ejecutar desde la raíz del backend:

```powershell
mvn '-Dtest=UsuarioServiceTest,ContactoServiceTest,AuthServiceTest,CarteraAgrupadaServiceTest,SegmentoMoraServiceTest,CarteraAgrupadaControllerTest,PadronControllerTest,SincronizacionControllerTest,UsuarioControllerTest' test
```

Los tests unitarios complementan los tests SQL existentes: no reemplazan la comprobación de consultas y restricciones reales.

## Actuator en prod

`ProdActuatorTest` carga la aplicación y el perfil `prod` real, con una base desechable explícita. Verifica health público sin detalles, `env`, `beans`, `configprops` y `heapdump` inaccesibles aun autenticado como administrador, y las propiedades de exposición/errores. Sin `APP_TEST_DB_URL` se omite; solo acepta bases locales cuyo nombre empiece por `app_test_`.

El script de la copia descrito abajo lo ejecuta automáticamente en una base distinta de la copia.
