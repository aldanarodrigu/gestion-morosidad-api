# Diagnóstico SUCIVE — 16/11/2026

Consultas directas de solo lectura, fuera de Spring y sin importar datos. Se usó el mismo usuario de la aplicación, fecha_corte=2026-11-16, include_total=0 y per_page=1000. Las pruebas fueron secuenciales; no representan una descarga completa.

La documentación OpenAPI 3.4.0 vigente confirma que `include_total=false` y `include_total=0` omiten el conteo, y que omitir fecha_inicio incluye todos los vencimientos anteriores al corte. Las respuestas 200 efectivamente tienen total y last_page nulos.

| Consulta | HTTP | Filas | Segundos |
|---|---:|---:|---:|
| Sin año mínimo, página 1 | 200 | 1000 | 9,47 |
| Sin año mínimo, página 51 | 504 | — | 60,06 |
| Desde 2013, página 51 | 200 | 1000 | 59,20 |
| Desde 2025, página 51 | 200 | 1000 | 42,61 |
| Desde 2013, CM 2663724, página 1 | 200 | 1 | 2,14 |

El CM consultado individualmente estaba en la página avanzada que tardó 59,20 segundos. La respuesta 504 provino de nginx, con contenido text/html. No contiene un error de validación de datos de nuestra aplicación.

## Interpretación y límites

Existe un caso reproducido de 504 incluso sin año mínimo y con el conteo desactivado. Las consultas profundas observadas son mucho más lentas que la primera página o un CM concreto; ampliar el período también coincide con mayor tiempo en estas pruebas. Sin embargo, son mediciones individuales, pueden influir carga o caché, y las páginas con distintos filtros no contienen necesariamente las mismas cuentas.

La página 51 desde 2013 respondió esta vez, mientras que en pruebas anteriores devolvía 504. El fallo no se debe a un límite fijo de 50.000 filas de nuestro cliente. Los 59,20 segundos están cerca del corte observado de aproximadamente 60 segundos, por lo que variaciones de carga pueden explicar que unas veces responda y otras falle. Es una hipótesis, no un diagnóstico confirmado del SQL.

Solicitar al proveedor el plan de ejecución y logs de pendientes: comprobar coste de paginación avanzada, filtros e índices y el timeout de aplicación/proxy. No puede atribuirse a un CM defectuoso específico ni confirmarse una implementación OFFSET sin acceso al servidor. Consultar por CM parece viable para casos individuales, pero estas pruebas no validan un mecanismo para enumerar y descargar todos los CM sin omisiones.

## URL reproducible sin año mínimo

```http
GET https://servicios.sanjose.gub.uy/utec_geopagos_service/api/v1/utec/sucive/facturas/pendientes?fecha_corte=2026-11-16&include_total=0&per_page=1000&page=51
```

Requiere HTTP Basic. Este informe no incluye credenciales ni documentos personales. No se modificaron código, configuración ni datos de la base durante este diagnóstico.
