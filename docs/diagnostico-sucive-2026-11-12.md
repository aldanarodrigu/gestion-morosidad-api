# Diagnóstico SUCIVE — 12/11/2026

Se consultó el estado de la sincronización del usuario sin interrumpirla. Terminó con ERROR después de validar 50 páginas/50.000 registros y agotar tres intentos en la página 51. No se inició otra sincronización. Luego se hicieron consultas directas de solo lectura a la API, sin pasar por Spring ni guardar datos en la base.

## Pruebas directas

En pendientes se usó `fecha_corte=2026-11-12&include_total=0`. El inicio fue 2013-01-01 salvo donde se indica 2025.

| Consulta | Resultado | Tiempo |
|---|---|---:|
| Pendientes, primera página, 1.000 filas | 200, 1.000 filas | 8,92 s |
| Pendientes del CM 1, misma fecha inicial | 200, una fila | 2,08 s |
| Pendientes desde 2013, posición 50.001, solo 100 filas | 504 nginx | 60,06 s |
| Pendientes desde 2025, posición 50.001, solo 100 filas | 200, 100 filas | 53,97 s |
| Contribuyentes SUCIVE, primera página, una fila | 200 | 2,05 s |

La consulta avanzada que falla es:

```http
GET https://servicios.sanjose.gub.uy/utec_geopagos_service/api/v1/utec/sucive/facturas/pendientes?fecha_inicio=2013-01-01&fecha_corte=2026-11-12&include_total=0&page=501&per_page=100
```

Requiere HTTP Basic; este informe no contiene credenciales. La página 501 de 100 equivale a comenzar en la misma posición que la página 51 de 1.000.

## Conclusión y límites

El 504 lo devuelve el servicio externo (respuesta HTML de nginx) después de aproximadamente 60 segundos, también fuera de nuestra aplicación. No es un fallo al guardar en PostgreSQL ni un problema de autenticación general: otras consultas del mismo servicio respondieron 200.

Los tiempos son compatibles con una consulta de pendientes costosa que empeora al avanzar por los resultados y ampliar el intervalo de fechas. Esto es una inferencia; sin acceso al SQL y al plan de ejecución del proveedor no se puede identificar la operación o índice concreto responsable. Cambiar el inicio a 2025 solo fue una prueba: no se modificó la configuración de 2013.

Para el proveedor: reproducir la URL anterior, revisar el tiempo del SQL y su plan, la aplicación de filtros de fecha y el coste de la paginación avanzada. Evaluar optimización, paginación por cursor/último CM o una exportación masiva. Revisar el límite del proxy/servicio junto con el rendimiento. Aumentar solamente el timeout de nuestro cliente no evita un 504 generado por nginx.

El código local ya omite el conteo total y reintenta páginas transitorias. Reducir únicamente `per_page` no resuelve la consulta avanzada probada. Los datos de SUCIVE anteriores se conservaron al fallar; este diagnóstico no cambió código, configuración ni datos de la base.
