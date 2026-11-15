# Validación de sincronización desde 2013 — 11/11/2026

Configuración persistida: año mínimo 2013. Se filtra por vencimiento desde 2013-01-01 incluido hasta 2026-11-11 excluido. No se filtra por año de facturación.

## Resultado en la base local

| Origen | Resultado | Deudas en cartera | Vencimientos anteriores a 2013 activos |
|---|---|---:|---:|
| Intendencia | Completada | 22.081 | 0 |
| SUCIVE | Falló la descarga externa; se conservaron sus datos anteriores | 119.230 | 24.101 |

**No quedó completado el objetivo para toda la cartera:** SUCIVE sigue pendiente de sincronizar. Su año aplicado permanece sin informar; guardar 2013 en configuración no reconstruye saldos existentes sin una importación completa.

Intendencia: ejecución 22:26–22:34, 22.081 pendientes y 9.214 cobros recibidos, 732 deudas creadas, 21.349 actualizadas, cero registros omitidos y cero advertencias. 1.468 registros quedaron fuera de la cartera del período, sin borrar estado ni gestiones. Cartera, agrupación y reportes reales verificados: 22.081 deudas, 21.413 contribuyentes agrupados, fecha mínima 2013-08-13.

## SUCIVE: diagnóstico reproducible

1. La consulta con conteo total devolvía 504. Con `include_total=0` comenzó a descargar y validar páginas.
2. Primer intento: falló después de superar 50.000 registros; no se guardó nada de esa descarga.
3. Segundo intento, con reintentos por página: superó la página 55 después de reintentar, pero la página 56 falló tres veces con HTTP 504. No se aplicaron cambios.
4. Pruebas de solo lectura sobre la posición 55.001: `per_page=100&page=551` respondió 200 en 57,31 s; `per_page=500&page=111` respondió 200 en 58,97 s.
5. La posición 60.001, `per_page=100&page=601`, respondió 504 en 60,12 s. Reducir el tamaño de página no permitió completar la descarga.

Ruta: `/api/v1/utec/sucive/facturas/pendientes`. En todas estas pruebas se utilizó `fecha_inicio=2013-01-01&fecha_corte=2026-11-11&include_total=0`, con HTTP Basic sin incluir credenciales en este documento.

Para comunicar al proveedor: revisar el rendimiento de páginas avanzadas y el límite de respuesta del servicio. El 504 fue devuelto por nginx, no por la validación local. No conviene subir la espera del cliente como única medida ante ese límite externo.

## Implementación y pruebas

- Configuración → Sincronización: año editable por Administrador e inicio manual por origen, con seguimiento en segundo plano.
- La tarea automática reutiliza el año guardado.
- Descarga sin COUNT mediante `has_more`, con validación de páginas, posiciones, duplicados y límites de fecha.
- Hasta tres intentos por página para 502/503/504 y errores de conexión.
- No se concilian ni guardan descargas incompletas.
- Los registros ausentes del período no se atribuyen automáticamente a pagos/cancelaciones.
- Suite de backend sin fallos: 312 pruebas ejecutadas y 68 condicionadas omitidas en esa ejecución. Las suites relevantes de persistencia se ejecutaron adicionalmente en bases aisladas y pasaron; también pasó una prueba aislada adicional de guardado/rechazo de años.
- Suite completa del frontend: 204 pruebas aprobadas. Compilación aprobada.

Pendiente: completar SUCIVE cuando la API permita descargar todo el período. Usar el botón manual para ese origen; no hace falta repetir Intendencia por este fallo.

Todos los cambios son locales. No se realizaron commits ni publicaciones en GitHub.
