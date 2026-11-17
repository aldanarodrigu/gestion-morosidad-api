# Pruebas de migraciones Flyway

`FlywayMigracionesTest` ejecuta 13 casos con el motor Flyway y PostgreSQL reales, sin iniciar la sincronización ni llamar a proveedores externos. Requiere `FLYWAY_TEST_DB_URL` explícita; solo admite bases locales cuyo nombre comience con `flyway_revision_`. No usa `.env` para elegir la base ni permite `clean` de Flyway. Cada caso crea un esquema independiente dentro de la base desechable.

## Cobertura

- Instalación de todas las migraciones en un esquema vacío y validación de checksums.
- Coincidencia entre scripts de código fuente y classpath: evita validar con restos de SQL borrados que permanecieron en `target`.
- Segunda ejecución sin reaplicar migraciones ni duplicar catálogos o datos.
- Actualización con datos ficticios desde 202610071300, 202610121930, 202610131200, 202611121000 y 202611161000.
- Conservación de IDs, documentos con ceros iniciales, relaciones, importes, estados, contactos, usuarios, gestiones y capturas existentes.
- Conversión de roles simples a múltiples y conservación de contraseñas y cuentas inactivas.
- Secuencias con bloques de 1000 posteriores al máximo ID existente.
- Conversión de rangos por tributo a historial sin asignarles un PadTipo arbitrario.
- Año mínimo opcional sin sustituir una configuración existente y ampliación de la fuente del contacto a 255 caracteres.
- Rechazo de un checksum adulterado exclusivamente en el historial del esquema de prueba.
- Error provocado con un rol antiguo inválido: rollback del DDL, conservación de datos y reintento después de corregir el dato ficticio.

Las actualizaciones se prueban con fixtures que representan versiones anteriores, no con una copia de datos personales de la base de trabajo. Las migraciones iniciales de septiembre declaran que la tabla de deuda todavía estaba vacía; no se afirma compatibilidad con datos cargados antes de esas transformaciones iniciales.

## Ejecución en PowerShell

Con PostgreSQL local disponible en `morosidad-local`, puerto 5434 y credenciales de pruebas `morosidad` / `morosidad-local`:

```powershell
$nombrePrueba = 'flyway_revision_' + [guid]::NewGuid().ToString('N')
docker exec morosidad-local createdb -U morosidad $nombrePrueba
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base desechable' }
try {
    $env:FLYWAY_TEST_DB_URL = "jdbc:postgresql://localhost:5434/$nombrePrueba"
    ./mvnw.cmd clean test '-Dtest=FlywayMigracionesTest'
} finally {
    Remove-Item Env:FLYWAY_TEST_DB_URL -ErrorAction SilentlyContinue
    docker exec morosidad-local dropdb -U morosidad --force $nombrePrueba
}
```

`mvn clean` limpia los artefactos de compilación; no equivale a Flyway clean y no borra la base de trabajo. No reemplazar la URL de pruebas por la del proyecto.

## Hallazgo del 17/11/2026

Faltaban en el código fuente `V202610121940__acciones_alcance_inicial.sql` y `V202610130020__descripcion_acciones.sql`, pero seguían presentes en los recursos compilados y figuraban aplicadas en la base local. Se restauraron sus bytes originales desde esos recursos, comprobando que sus checksums Flyway coinciden con los registrados: 2128438556 y 486776609, respectivamente.

La consulta de las 31 entradas aplicadas del historial local fue de solo lectura: después de la restauración no quedan archivos faltantes ni checksums distintos. No se ejecutó `repair`, no se reescribió el historial y no se aplicaron migraciones sobre la base de trabajo.
