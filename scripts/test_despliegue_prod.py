"""Pruebas reproducibles de Compose y, con --runtime, de un despliegue Docker aislado."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import sys
import tempfile
import time
import unittest
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
QA = None


class Entorno:
    def __init__(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="morosidad-prod-qa-")
        self.carpeta = Path(self.tmp.name)
        self.proyecto = "morosidad-prod-qa-" + uuid.uuid4().hex[:10]
        sockets = [socket.socket() for _ in range(3)]
        try:
            for s in sockets:
                s.bind(("127.0.0.1", 0))
            db, api, front = [s.getsockname()[1] for s in sockets]
        finally:
            for s in sockets:
                s.close()
        self.valores = {
            "DB_NAME": "morosidad_qa", "DB_USER": "morosidad", "DB_PASSWORD": secrets.token_hex(24),
            "JWT_SECRET": base64.b64encode(secrets.token_bytes(32)).decode(),
            "ADMIN_BOOTSTRAP_PASSWORD": secrets.token_urlsafe(24),
            "DB_HOST_PORT": str(db), "API_HOST_PORT": str(api), "FRONT_HOST_PORT": str(front),
            "SINCRONIZACION_AUTOMATICA": "false", "UTEC_API_USERNAME": "", "UTEC_API_PASSWORD": "",
            "UTEC_API_PAGE_SIZE": "1000", "SINCRONIZACION_CRON": "0 0 3 * * *",
        }
        self.env = os.environ.copy()
        for k in self.valores:
            self.env.pop(k, None)
        self.archivo = self.envfile(self.valores, "qa.env")
        self.api = "http://127.0.0.1:" + str(api)
        self.front = "http://127.0.0.1:" + str(front)
        self.token = None
        self.creado = False

    def envfile(self, valores, nombre):
        p = self.carpeta / nombre
        p.write_text("".join(k + "=" + v + "\n" for k, v in valores.items()), encoding="utf-8")
        return p

    def compose(self, *args, archivo=None, check=True, timeout=180):
        cmd = ["docker", "compose", "--project-directory", str(ROOT), "--env-file", str(archivo or self.archivo), "-p", self.proyecto, *args]
        r = subprocess.run(cmd, cwd=ROOT, env=self.env, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
        if check and r.returncode:
            mensaje = (r.stdout + r.stderr)[-6000:]
            for k in ("DB_PASSWORD", "JWT_SECRET"):
                mensaje = mensaje.replace(self.valores[k], "[oculto]")
            raise RuntimeError("Compose falló:\n" + mensaje)
        return r

    def request(self, ruta, *, front=False, datos=None, token=False, metodo=None):
        headers = {"Accept": "application/json"}
        if datos is not None:
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = "Bearer " + self.token
        req = urllib.request.Request((self.front if front else self.api) + ruta,
                                     data=None if datos is None else json.dumps(datos).encode(), headers=headers, method=metodo)
        try:
            r = urllib.request.urlopen(req, timeout=15)
        except urllib.error.HTTPError as e:
            r = e
        with r:
            return r.status, dict(r.headers), r.read()

    def levantar(self):
        existentes = subprocess.check_output(["docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=" + self.proyecto], text=True)
        if existentes.strip():
            raise RuntimeError("El proyecto de pruebas ya existe; no se reutiliza")
        print("Construyendo imágenes del backend y frontend para " + self.proyecto, flush=True)
        self.compose("build", timeout=1200)
        self.creado = True
        self.compose("up", "-d", timeout=180)
        print("Esperando health y frontend del despliegue de pruebas...", flush=True)
        limite = time.monotonic() + 180
        while time.monotonic() < limite:
            try:
                status, _, body = self.request("/actuator/health")
                front, _, _ = self.request("/", front=True)
                if status == 200 and json.loads(body).get("status") == "UP" and front == 200:
                    break
            except (OSError, ValueError):
                pass
            time.sleep(2)
        else:
            raise RuntimeError("El despliegue aislado no quedó saludable en 180 segundos")
        status, _, body = self.request("/api/auth/login", front=True, datos={"username": "admin", "password": self.valores["ADMIN_BOOTSTRAP_PASSWORD"]})
        if status != 200:
            raise RuntimeError("No se pudo iniciar sesión en la base nueva de QA; HTTP " + str(status))
        self.token = json.loads(body)["token"]

    def contenedor(self, servicio):
        return self.compose("ps", "-q", servicio).stdout.strip()

    def cerrar(self):
        if self.creado:
            self.compose("down", "--volumes", "--remove-orphans", timeout=120)
            print("Contenedores, red y volumen de QA eliminados: " + self.proyecto, flush=True)
        self.tmp.cleanup()


class ConfiguracionComposeTest(unittest.TestCase):
    def test_secret_jwt_obligatorio(self):
        valores = dict(QA.valores, JWT_SECRET="")
        r = QA.compose("config", archivo=QA.envfile(valores, "sin-jwt.env"), check=False)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("JWT_SECRET", r.stderr)

    def test_password_bd_obligatorio(self):
        valores = dict(QA.valores, DB_PASSWORD="")
        r = QA.compose("config", archivo=QA.envfile(valores, "sin-password.env"), check=False)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("DB_PASSWORD", r.stderr)

    def test_puertos_y_aislamiento_del_backend(self):
        cfg = json.loads(QA.compose("config", "--format", "json").stdout)
        for servicio in ("db", "api", "frontend"):
            for puerto in cfg["services"][servicio]["ports"]:
                self.assertEqual(puerto["host_ip"], "127.0.0.1")
        api = cfg["services"]["api"]
        self.assertTrue(api["read_only"])
        self.assertIn("/tmp", api["tmpfs"])
        self.assertTrue(any("no-new-privileges" in s for s in api["security_opt"]))
        self.assertEqual(api["depends_on"]["db"]["condition"], "service_healthy")
        self.assertEqual(api["environment"]["SINCRONIZACION_AUTOMATICA"], "false")

    def test_contextos_de_build_excluyen_secretos(self):
        for repo in (ROOT, ROOT.parent / "gestion-morosidad-frontend"):
            lineas = (repo / ".dockerignore").read_text(encoding="utf-8").splitlines()
            self.assertIn(".env", lineas)
            self.assertIn(".env.*", lineas)
            self.assertIn(".git", lineas)


class DespliegueProdTest(unittest.TestCase):
    def test_health_sin_detalles_internos(self):
        status, _, body = QA.request("/actuator/health")
        self.assertEqual(status, 200)
        datos = json.loads(body)
        self.assertEqual(datos["status"], "UP")
        self.assertTrue(set(datos).issubset({"status", "groups"}))
        self.assertTrue(set(datos.get("groups", [])).issubset({"liveness", "readiness"}))
        self.assertNotIn("components", datos)
        self.assertNotIn("details", datos)

    def test_actuator_solo_expone_health(self):
        for ruta in ("/actuator/env", "/actuator/configprops", "/actuator/beans", "/actuator/heapdump"):
            status, _, _ = QA.request(ruta, token=True)
            self.assertEqual(status, 404, ruta)

    def test_api_protegida_y_token_invalido(self):
        self.assertEqual(QA.request("/api/deudas")[0], 401)
        token = QA.token
        try:
            QA.token = "token-invalido"
            self.assertEqual(QA.request("/api/deudas", token=True)[0], 401)
        finally:
            QA.token = token
        self.assertEqual(QA.request("/api/deudas", front=True, token=True)[0], 200)

    def test_login_incorrecto_no_revela_informacion(self):
        status, _, body = QA.request("/api/auth/login", datos={"username": "admin", "password": "NO_ES_LA_CLAVE"})
        self.assertEqual(status, 401)
        datos = json.loads(body)
        self.assertNotIn("trace", datos)
        self.assertNotIn("exception", datos)
        self.assertNotIn("NO_ES_LA_CLAVE", body.decode())

    def test_ruta_inexistente_es_404_y_datos_obligatorios_son_400(self):
        status, _, body = QA.request("/api/ruta-qa-inexistente", token=True)
        self.assertEqual(status, 404)
        self.assertNotIn("trace", json.loads(body))
        status, _, body = QA.request("/api/auth/login", datos={})
        self.assertEqual(status, 400)
        self.assertNotIn("exception", json.loads(body))

    def test_frontend_y_ruta_spa_con_cabeceras(self):
        for ruta in ("/", "/cartera", "/configuracion"):
            status, headers, body = QA.request(ruta, front=True)
            self.assertEqual(status, 200)
            h = {k.lower(): v for k, v in headers.items()}
            self.assertIn("text/html", h.get("content-type", ""))
            self.assertEqual(h.get("x-content-type-options"), "nosniff")
            self.assertEqual(h.get("x-frame-options"), "DENY")
            self.assertIn("frame-ancestors 'none'", h.get("content-security-policy", ""))
            self.assertIsNotNone(re.search(br'/assets/[^" ]+[.]js', body))

    def test_perfil_prod_y_usuario_sin_privilegios(self):
        cid = QA.contenedor("api")
        datos = json.loads(subprocess.check_output(["docker", "inspect", cid], text=True))[0]
        self.assertNotIn(datos["Config"]["User"], ("", "root", "0", "0:0"))
        self.assertIn("SPRING_PROFILES_ACTIVE=prod", datos["Config"]["Env"])
        self.assertTrue(datos["HostConfig"]["ReadonlyRootfs"])
        for servicio in ("api", "frontend"):
            uid = subprocess.check_output(["docker", "exec", QA.contenedor(servicio), "id", "-u"], text=True).strip()
            self.assertNotEqual(uid, "0")

    def test_reinicio_conserva_configuracion_y_no_reaplica_migraciones(self):
        status, _, _ = QA.request("/api/sincronizaciones/configuracion", datos={"anioMinimo": 2015}, token=True, metodo="PUT")
        self.assertEqual(status, 200)
        QA.compose("restart", "api")
        limite = time.monotonic() + 90
        while time.monotonic() < limite:
            try:
                if QA.request("/actuator/health")[0] == 200:
                    break
            except OSError:
                pass
            time.sleep(2)
        else:
            self.fail("El backend de QA no volvió a estar saludable")
        status, _, body = QA.request("/api/sincronizaciones/configuracion", token=True)
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body)["anioMinimo"], 2015)
        cid = QA.contenedor("db")
        repetidas = subprocess.check_output(["docker", "exec", cid, "psql", "-U", "morosidad", "-d", "morosidad_qa", "-Atc",
                                            "SELECT count(*) FROM (SELECT version FROM flyway_schema_history GROUP BY version HAVING count(*) > 1) repetidas"], text=True).strip()
        self.assertEqual(int(repetidas), 0)

    def test_conexiones_observadas_y_migraciones_aplicadas(self):
        cid = QA.contenedor("db")
        valor = subprocess.check_output(["docker", "exec", cid, "psql", "-U", "morosidad", "-d", "morosidad_qa", "-Atc",
                                         "SELECT count(*) FROM flyway_schema_history WHERE success"], text=True).strip()
        esperadas = len(list((ROOT / "src/main/resources/db/migration").glob("V*.sql")))
        self.assertEqual(int(valor), esperadas)
        salida = subprocess.check_output(["docker", "exec", cid, "psql", "-U", "morosidad", "-d", "morosidad_qa", "-Atc",
                                          "SELECT count(*) FROM pg_stat_activity WHERE datname='morosidad_qa' AND application_name='PostgreSQL JDBC Driver'"], text=True).strip()
        self.assertLessEqual(int(salida), 3)


def main():
    global QA
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", action="store_true", help="Construye y ejecuta el stack completo en un proyecto Docker desechable")
    args = parser.parse_args()
    QA = Entorno()
    try:
        suite = unittest.defaultTestLoader.loadTestsFromTestCase(ConfiguracionComposeTest)
        resultado = unittest.TextTestRunner(verbosity=2).run(suite)
        if not resultado.wasSuccessful():
            return 1
        if args.runtime:
            QA.levantar()
            suite = unittest.defaultTestLoader.loadTestsFromTestCase(DespliegueProdTest)
            resultado = unittest.TextTestRunner(verbosity=2).run(suite)
            if not resultado.wasSuccessful():
                return 1
        return 0
    finally:
        QA.cerrar()


if __name__ == "__main__":
    sys.exit(main())
