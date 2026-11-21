"""Prueba Flyway sobre una copia local anonimizada; elimina únicamente las bases que crea.
Requiere Docker morosidad-local y Maven. No escribe ni exporta datos de la base origen.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import uuid

ROOT = Path(__file__).resolve().parents[1]
DOCKER = ["docker", "exec", "morosidad-local"]
MAVEN = r"C:\Program Files\Apache\Maven\apache-maven-3.9.11\bin\mvn.cmd"

def command(args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, **kwargs)

def sql(db, statement):
    # Nunca imprime las filas ni el detalle de un error que pudiera contener datos.
    result = subprocess.run(["docker", "exec", "-i", "morosidad-local", "psql", "-U", "morosidad", "-d", db,
                             "-v", "ON_ERROR_STOP=1", "-At"], input=statement.encode(), capture_output=True)
    if result.returncode:
        raise RuntimeError("Falló una operación SQL en la copia aislada (salida omitida para proteger datos).")
    return result.stdout.decode().strip()

def main():
    suffix = uuid.uuid4().hex
    copia = "flyway_copia_" + suffix
    app = "app_test_" + suffix
    revision = "flyway_revision_" + suffix
    created = []
    try:
        for db in (copia, app, revision):
            command(DOCKER + ["createdb", "-U", "morosidad", db])
            created.append(db)
        sql(copia, "DROP SCHEMA public")
        # Pipe binario directo: pg_dump mantiene una instantánea consistente; no hay archivo con datos reales.
        with tempfile.TemporaryFile() as dump_errors:
            dump = subprocess.Popen(DOCKER + ["pg_dump", "-U", "morosidad", "-d", "morosidad", "--schema=public",
                                               "--no-owner", "--no-privileges", "-Fc"], stdout=subprocess.PIPE, stderr=dump_errors)
            try:
                restore = subprocess.run(["docker", "exec", "-i", "morosidad-local", "pg_restore", "-U", "morosidad",
                                          "-d", copia, "--no-owner", "--no-privileges", "--exit-on-error"],
                                         stdin=dump.stdout, capture_output=True, timeout=600)
                dump.stdout.close()
                dump_code = dump.wait(timeout=30)
                if restore.returncode or dump_code:
                    raise RuntimeError("No se pudo crear la copia; no se muestran datos del dump.")
            finally:
                if dump.poll() is None:
                    dump.kill()
                    dump.wait()
        # Pseudónimos estables dentro de esta ejecución: preservan igualdad, IDs y relaciones.
        # Salt efímero no se guarda; valores libres de contacto/observaciones se sustituyen completamente.
        salt = uuid.uuid4().hex
        sql(copia, f"""
BEGIN;
UPDATE contribuyentes SET nombre='Persona de prueba '||id,
 documento=CASE WHEN documento IS NULL THEN NULL WHEN trim(documento)='' THEN '' ELSE 'D'||md5('{salt}'||trim(documento)) END;
UPDATE padrones SET cm='QA-'||md5('{salt}'||origen||':'||cm),
 numero_padron='P'||md5('{salt}'||numero_padron),
 matricula=CASE WHEN matricula IS NULL THEN NULL ELSE 'M'||md5('{salt}'||matricula) END,
 localidad=CASE WHEN localidad IS NULL THEN NULL ELSE 'Localidad de prueba' END, block=NULL, unidad=NULL;
UPDATE contactos SET valor='Dato de prueba '||id,
 origen=CASE WHEN origen IN ('API_MUNICIPAL','API_GEOPAGOS','API_GEOPAT') THEN origen ELSE 'Fuente de prueba' END;
UPDATE usuarios SET username='usuario_prueba_'||id,password='credencial_inutilizada_para_pruebas';
UPDATE gestiones SET observacion=CASE WHEN observacion IS NULL THEN NULL ELSE 'Observacion de prueba' END;
UPDATE capturas_cartera SET nombre='Captura de prueba '||id,usuario='Usuario de prueba';
UPDATE captura_cuentas SET cm='QA-'||md5('{salt}'||origen||':'||cm),
 numero_padron='P'||md5('{salt}'||numero_padron),nombre='Persona de prueba',
 documento=CASE WHEN documento IS NULL THEN NULL WHEN trim(documento)='' THEN '' ELSE 'D'||md5('{salt}'||trim(documento)) END,
 motivo_ultimo_cobro=CASE WHEN motivo_ultimo_cobro IS NULL THEN NULL ELSE 'Motivo de prueba' END;
UPDATE deuda SET motivo_ultimo_cobro=CASE WHEN motivo_ultimo_cobro IS NULL THEN NULL ELSE 'Motivo de prueba' END;
UPDATE acciones SET nombre='Accion de prueba '||id,descripcion=CASE WHEN descripcion IS NULL THEN NULL ELSE 'Descripcion de prueba' END;
UPDATE tributos SET descripcion=CASE WHEN descripcion IS NULL THEN NULL ELSE 'Tributo de prueba '||id END;
UPDATE segmentos_mora SET descripcion=CASE WHEN descripcion IS NULL THEN NULL ELSE 'Segmento de prueba' END;
DO $$
BEGIN
    IF to_regclass('public.cancelaciones') IS NOT NULL THEN
        UPDATE cancelaciones SET cm='QA-'||md5('{salt}'||origen||':'||cm),
         numero_padron=CASE WHEN numero_padron IS NULL THEN NULL ELSE 'P'||md5('{salt}'||numero_padron) END,
         numero_cobro='C'||md5('{salt}'||origen||':'||numero_cobro),
         persona=CASE WHEN persona IS NULL THEN NULL ELSE 'Persona de prueba' END,
         documento=CASE WHEN documento IS NULL THEN NULL WHEN trim(documento)='' THEN '' ELSE 'D'||md5('{salt}'||trim(documento)) END,
         motivo=CASE WHEN motivo IS NULL THEN NULL ELSE 'Motivo de prueba' END;
    END IF;
END $$;
COMMIT;
""")
        print("Copia anonimizada: " + sql(copia, "SELECT count(*)||' contribuyentes' FROM contribuyentes"), flush=True)
        env = os.environ.copy()
        env.update(FLYWAY_COPIA_TEST_DB_URL=f"jdbc:postgresql://localhost:5434/{copia}",
                   APP_TEST_DB_URL=f"jdbc:postgresql://localhost:5434/{app}",
                   FLYWAY_TEST_DB_URL=f"jdbc:postgresql://localhost:5434/{revision}")
        tests = ("FlywayCopiaActualTest,FlywayMigracionesTest,ProdActuatorTest,UsuarioServiceTest,AuthServiceTest,"
                 "ContactoServiceTest,CarteraAgrupadaServiceTest,SegmentoMoraServiceTest,CarteraAgrupadaControllerTest,"
                 "PadronControllerTest,SincronizacionControllerTest,UsuarioControllerTest")
        # El log solo contiene la copia ya anonimizada y fixtures de pruebas.
        log = Path(tempfile.gettempdir()) / ("morosidad_revision_" + suffix + ".log")
        with log.open("w", encoding="utf-8") as output:
            result = subprocess.run([MAVEN, "-Dtest="+tests, "test"], cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
        print("\n".join(log.read_text(encoding="utf-8", errors="replace").splitlines()[-24:]))
        print("Log: " + str(log))
        return result.returncode
    finally:
        for db in reversed(created):
            command(DOCKER + ["dropdb", "-U", "morosidad", "--force", db])
        print("Bases aisladas eliminadas; base de trabajo sin cambios.", flush=True)

if __name__ == "__main__":
    sys.stdout.reconfigure(errors="replace")
    raise SystemExit(main())
