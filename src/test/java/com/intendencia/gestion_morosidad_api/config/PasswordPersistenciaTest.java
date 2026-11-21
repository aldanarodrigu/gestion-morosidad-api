package com.intendencia.gestion_morosidad_api.config;

import com.intendencia.gestion_morosidad_api.modules.auth.LimitadorLogin;
import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import com.intendencia.gestion_morosidad_api.modules.usuario.Usuario;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "APP_TEST_DB_URL", matches = ".+")
@TestPropertySource(locations = {"classpath:application.properties", "classpath:application-prod.properties"})
@SpringBootTest(properties = {"spring.config.name=arranque-aislado", "spring.config.import=",
    "sincronizacion.automatica.habilitada=false", "geopagos.username=", "geopagos.password=",
    "ADMIN_BOOTSTRAP_PASSWORD=clave-administrador-prueba", "auth.login.max-fallos=5",
    "auth.login.ventana-segundos=900", "auth.login.bloqueo-segundos=900"})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class PasswordPersistenciaTest {
    private static final String ACTUAL = "clave-anterior-prueba";
    private static final String NUEVA = "clave-nueva-de-prueba";
    private static final String ADMIN = "clave-administrador-prueba";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired LimitadorLogin limitador;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        String url = System.getenv("APP_TEST_DB_URL");
        assertThat(url).matches("jdbc:postgresql://(localhost|127[.]0[.]0[.]1):[0-9]+/app_test_[a-z0-9_]+");
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> "morosidad");
        r.add("spring.datasource.password", () -> "morosidad-local");
        r.add("jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    void cambiaSoloLaCuentaAutenticadaEInvalidaSuToken() throws Exception {
        Usuario cuenta = crear(Rol.NOTIFICADOR);
        Usuario otra = crear(Rol.ANALISTA);
        String token = iniciarSesion(cuenta.getUsername(), ACTUAL, false);
        String tokenOtra = iniciarSesion(otra.getUsername(), ACTUAL, false);

        mvc.perform(put("/api/auth/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content(cambio(ACTUAL, NUEVA)))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/auth/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(cambio("otra-clave", NUEVA)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/auth/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(cambio(ACTUAL, "á".repeat(37))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/auth/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(cambio(ACTUAL, ACTUAL)))
                .andExpect(status().isBadRequest());

        cambiar(token, ACTUAL, NUEVA);
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", tokenOtra)).andExpect(status().isOk());
        rechazarLogin(cuenta.getUsername(), ACTUAL);
        iniciarSesion(cuenta.getUsername(), NUEVA, false);
        var guardada = usuarios.findById(cuenta.getId()).orElseThrow();
        assertThat(guardada.getPassword()).isNotEqualTo(NUEVA);
        assertThat(encoder.matches(NUEVA, guardada.getPassword())).isTrue();
        assertThat(guardada.getRoles()).containsExactly(Rol.NOTIFICADOR);
    }

    @Test
    void resetRequiereAdminYObligaACambiarLaClaveTemporalAntesDeEntrar() throws Exception {
        Usuario cuenta = crear(Rol.ADMINISTRADOR, Rol.ANALISTA);
        Usuario analista = crear(Rol.ANALISTA);
        String tokenAnterior = iniciarSesion(cuenta.getUsername(), ACTUAL, false);
        String tokenAnalista = iniciarSesion(analista.getUsername(), ACTUAL, false);
        String tokenAdmin = iniciarSesion("admin", ADMIN, false);
        String ruta = "/api/usuarios/" + cuenta.getId() + "/password/reset";

        mvc.perform(post(ruta).header("Authorization", tokenAnalista)).andExpect(status().isForbidden());
        mvc.perform(post(ruta).header("Authorization", tokenAnterior)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/usuarios/999999999/password/reset").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        String respuesta = mvc.perform(post(ruta).header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.passwordTemporal").isString()).andExpect(jsonPath("$.venceEn").exists())
                .andReturn().getResponse().getContentAsString();
        String temporal = JsonPath.read(respuesta, "$.passwordTemporal");
        assertThat(usuarios.findById(cuenta.getId()).orElseThrow().getPassword()).isNotEqualTo(temporal);

        rechazarLogin(cuenta.getUsername(), ACTUAL);
        mvc.perform(get("/api/auth/me").header("Authorization", tokenAnterior)).andExpect(status().isUnauthorized());
        String tokenTemporal = iniciarSesion(cuenta.getUsername(), temporal, true);
        mvc.perform(get("/api/auth/me").header("Authorization", tokenTemporal))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiereCambioPassword").value(true));
        for (String path : new String[]{"/api/deudas", "/api/usuarios", "/api/reportes/cartera"}) {
            mvc.perform(get(path).header("Authorization", tokenTemporal)).andExpect(status().isForbidden());
        }
        mvc.perform(post(ruta).header("Authorization", tokenTemporal)).andExpect(status().isForbidden());

        cambiar(tokenTemporal, temporal, NUEVA);
        mvc.perform(get("/api/auth/me").header("Authorization", tokenTemporal)).andExpect(status().isUnauthorized());
        rechazarLogin(cuenta.getUsername(), temporal);
        String vigente = iniciarSesion(cuenta.getUsername(), NUEVA, false);
        mvc.perform(get("/api/usuarios").header("Authorization", vigente)).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").header("Authorization", tokenAdmin)).andExpect(status().isOk());
    }

    @Test
    void unaClaveTemporalVencidaYaNoPermiteIngresarNiUsarSuToken() throws Exception {
        Usuario cuenta = crear(Rol.ANALISTA);
        String vigente = iniciarSesion(cuenta.getUsername(), ACTUAL, false);
        Instant vence = Instant.now().plusSeconds(600);
        assertThat(usuarios.actualizarCredencial(cuenta.getId(), 0, encoder.encode(ACTUAL), vence)).isEqualTo(1);
        String tokenTemporal = iniciarSesion(cuenta.getUsername(), ACTUAL, true);
        // Simula que vence el plazo conservando el número de versión, para probar el control de vigencia del JWT.
        jdbc.update("UPDATE usuarios SET password_temporal_hasta = now() - interval '1 second' WHERE id = ?", cuenta.getId());
        rechazarLogin(cuenta.getUsername(), ACTUAL);
        mvc.perform(get("/api/auth/me").header("Authorization", vigente)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", tokenTemporal)).andExpect(status().isUnauthorized());
    }

    @Test
    void prodNoAceptaLaClaveLocalPorDefecto() throws Exception {
        rechazarLogin("admin", "admin123");
        iniciarSesion("admin", ADMIN, false);
    }

    @Test
    void cincoFallosBloqueanLaCuentaYReintentarNoProlongaElBloqueo() throws Exception {
        String cuenta = crear(Rol.ANALISTA).getUsername();
        String otraCuenta = crear(Rol.NOTIFICADOR).getUsername();
        String tokenVigente = iniciarSesion(cuenta, ACTUAL, false);
        for (int i = 0; i < 5; i++) {
            rechazarLogin(i % 2 == 0 ? cuenta : " " + cuenta.toUpperCase(Locale.ROOT) + " ", "clave-incorrecta");
        }
        OffsetDateTime vence = jdbc.queryForObject(
                "SELECT vence_en FROM intentos_login WHERE cuenta = ?", OffsetDateTime.class, cuenta);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(cuenta, ACTUAL)))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("Demasiados intentos")));
        assertThat(jdbc.queryForObject("SELECT vence_en FROM intentos_login WHERE cuenta = ?", OffsetDateTime.class, cuenta))
                .isEqualTo(vence);
        iniciarSesion(otraCuenta, ACTUAL, false);
        mvc.perform(get("/api/auth/me").header("Authorization", tokenVigente)).andExpect(status().isOk());

        vencerContador(cuenta);
        iniciarSesion(cuenta, ACTUAL, false);
        assertThat(filasDeIntentos(cuenta)).isZero();
    }

    @Test
    void elLoginCorrectoReiniciaElContadorYLosFallosFueraDeLaVentanaNoSeAcumulan() throws Exception {
        String cuenta = crear(Rol.ANALISTA).getUsername();
        for (int i = 0; i < 4; i++) rechazarLogin(cuenta, "clave-incorrecta");
        iniciarSesion(cuenta, ACTUAL, false);
        assertThat(filasDeIntentos(cuenta)).isZero();

        for (int i = 0; i < 4; i++) rechazarLogin(cuenta, "clave-incorrecta");
        vencerContador(cuenta);
        rechazarLogin(cuenta, "clave-incorrecta");
        assertThat(jdbc.queryForObject("SELECT fallos FROM intentos_login WHERE cuenta = ?", Integer.class, cuenta))
                .isEqualTo(1);
        iniciarSesion(cuenta, ACTUAL, false);
    }

    @Test
    void peticionesSimultaneasCompartenLimiteInclusoParaUnUsuarioInexistente() throws Exception {
        String cuenta = "inexistente-" + UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var intentos = IntStream.range(0, 8).mapToObj(i -> (Callable<Integer>) () ->
                    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                    .content(login(cuenta, "clave-incorrecta")))
                            .andReturn().getResponse().getStatus()).toList();
            var estados = new java.util.ArrayList<Integer>();
            for (var resultado : executor.invokeAll(intentos)) estados.add(resultado.get());
            assertThat(estados).containsExactlyInAnyOrder(401, 401, 401, 401, 401, 429, 429, 429);
        }

        assertThat(jdbc.queryForObject("SELECT fallos FROM intentos_login WHERE cuenta = ?", Integer.class, cuenta))
                .isEqualTo(5);
        vencerContador(cuenta);
        limitador.limpiarVencidos();
        assertThat(filasDeIntentos(cuenta)).isZero();
    }

    private long filasDeIntentos(String cuenta) {
        return jdbc.queryForObject("SELECT count(*) FROM intentos_login WHERE cuenta = ?", Long.class, cuenta);
    }

    private void vencerContador(String cuenta) {
        jdbc.update("UPDATE intentos_login SET vence_en = now() - interval '1 second' WHERE cuenta = ?", cuenta);
    }

    private Usuario crear(Rol... roles) {
        return usuarios.saveAndFlush(new Usuario(null, "qa-" + UUID.randomUUID(), encoder.encode(ACTUAL),
                Set.of(roles), true));
    }

    private String iniciarSesion(String usuario, String password, boolean requiereCambio) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(usuario, password)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiereCambioPassword").value(requiereCambio))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(respuesta, "$.token");
    }

    private void rechazarLogin(String usuario, String password) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(usuario, password)))
                .andExpect(status().isUnauthorized());
    }

    private void cambiar(String token, String actual, String nueva) throws Exception {
        mvc.perform(put("/api/auth/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(cambio(actual, nueva)))
                .andExpect(status().isNoContent());
    }

    private static String cambio(String actual, String nueva) {
        return "{\"passwordActual\":\"%s\",\"passwordNueva\":\"%s\"}".formatted(actual, nueva);
    }

    private static String login(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }
}
