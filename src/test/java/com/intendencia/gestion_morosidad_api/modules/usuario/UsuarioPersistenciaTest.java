package com.intendencia.gestion_morosidad_api.modules.usuario;

import com.intendencia.gestion_morosidad_api.modules.accion.Accion;
import com.intendencia.gestion_morosidad_api.modules.accion.AccionRepository;
import com.intendencia.gestion_morosidad_api.modules.auth.TokenService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.http.MediaType;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "USUARIOS_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = "sincronizacion.automatica.habilitada=false")
@AutoConfigureMockMvc
@Transactional
class UsuarioPersistenciaTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("USUARIOS_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> "morosidad");
        r.add("spring.datasource.password", () -> "morosidad-local");
    }
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired AccionRepository acciones;
    @Autowired PasswordEncoder encoder;
    @Autowired TokenService tokens;
    @Autowired JwtDecoder decoder;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired UsuarioService servicio;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    Usuario crear(String nombre, Rol... roles) {
        Usuario u = new Usuario(); u.setUsername(nombre); u.setPassword(encoder.encode("prueba123"));
        u.setRoles(Set.of(roles)); return usuarios.saveAndFlush(u);
    }
    Accion accion() {
        Accion a = new Accion(); a.setNombre("Prueba permisos"); return acciones.saveAndFlush(a);
    }

    @Test void creaUsuarioConVariosRolesYLoginEmiteTodos() throws Exception {
        var respuesta = mvc.perform(post("/api/usuarios").with(user("admin-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"username":"nuevo-test","password":"clave-de-prueba-larga","roles":["NOTIFICADOR","ADMINISTRADOR"]}
                          """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", hasItems("NOTIFICADOR", "ADMINISTRADOR")))
                .andExpect(jsonPath("$.password").doesNotExist()).andReturn();
        entityManager.flush();
        entityManager.clear();
        Usuario u = usuarios.findByUsername("nuevo-test").orElseThrow();
        assertThat(u.getRoles()).containsExactlyInAnyOrder(Rol.NOTIFICADOR, Rol.ADMINISTRADOR);
        assertThat(encoder.matches("clave-de-prueba-larga", u.getPassword())).isTrue();
        assertThat(u.requiereCambioPassword()).isTrue();
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nuevo-test\",\"password\":\"clave-de-prueba-larga\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereCambioPassword").value(true))
                .andExpect(jsonPath("$.roles", hasItems("NOTIFICADOR", "ADMINISTRADOR"))).andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");
        assertThat(decoder.decode(token).getClaimAsStringList("roles"))
                .containsExactlyInAnyOrder("NOTIFICADOR", "ADMINISTRADOR");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(2));
    }

    @Test void multiplesRolesPermitenAdministrarYRevocarUnRolAfectaElMismoToken() throws Exception {
        Usuario u = crear("combinado-test", Rol.NOTIFICADOR, Rol.ADMINISTRADOR);
        String token = tokens.generateToken(u);
        Long id = accion().getId();
        mvc.perform(post("/api/acciones").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Creada por usuario mixto\",\"segmentoId\":1}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/acciones/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/usuarios/" + u.getId()).with(user("otro-admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"combinado-test\",\"roles\":[\"NOTIFICADOR\"],\"activo\":true}"))
                .andExpect(status().isOk());
        usuarios.flush();
        entityManager.clear();
        mvc.perform(put("/api/acciones/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/acciones").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Sin permiso\",\"segmentoId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(1));
    }

    @Test void soloAdministradoresAdministranUsuarios() throws Exception {
        for (Rol rol : new Rol[]{Rol.ANALISTA, Rol.NOTIFICADOR}) {
            String token = tokens.generateToken(crear("usuario-" + rol, rol));
            mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"prohibido\",\"password\":\"prueba123\",\"roles\":[\"ADMINISTRADOR\"]}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/usuarios/1").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"roles\":[\"ADMINISTRADOR\"],\"activo\":true}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(usuarios.existsByUsername("prohibido")).isFalse();
    }

    @Test void cuentaDesactivadaNoUsaSuTokenNiIniciaSesion() throws Exception {
        Usuario u = crear("inactivo-test", Rol.ADMINISTRADOR);
        String token = tokens.generateToken(u);
        mvc.perform(put("/api/usuarios/" + u.getId()).with(user("admin-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"inactivo-test\",\"roles\":[\"ADMINISTRADOR\"],\"activo\":false}"))
                .andExpect(status().isOk());
        usuarios.flush();
        entityManager.clear();
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"inactivo-test\",\"password\":\"prueba123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void administradorReactivaCuentaSinCambiarSuContrasena() throws Exception {
        Usuario u = crear("reactivar-test", Rol.ANALISTA);
        String password = u.getPassword();
        u.setActivo(false); usuarios.saveAndFlush(u); entityManager.clear();
        mvc.perform(put("/api/usuarios/" + u.getId()).with(user("admin-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reactivar-test\",\"roles\":[\"ANALISTA\",\"NOTIFICADOR\"],\"activo\":true}"))
                .andExpect(status().isOk());
        usuarios.flush(); entityManager.clear();
        assertThat(usuarios.findById(u.getId()).orElseThrow().getPassword()).isEqualTo(password);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reactivar-test\",\"password\":\"prueba123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItems("ANALISTA", "NOTIFICADOR")));
    }

    @Test void migracionConservaLosRolesDeUsuariosExistentes() throws Exception {
        var connection = entityManager.unwrap(org.hibernate.Session.class)
                .doReturningWork(c -> c);
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA usuarios_migracion_test");
            statement.execute("SET LOCAL search_path TO usuarios_migracion_test");
        }
        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                new org.springframework.core.io.ClassPathResource("db/migration/V202609240100__crear_tabla_usuarios.sql"));
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO usuarios (username,password,rol) VALUES
                    ('a','hash','ADMINISTRADOR'),('n','hash','NOTIFICADOR'),('l','hash','ANALISTA')
                    """);
        }
        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                new org.springframework.core.io.ClassPathResource("db/migration/V202610130100__usuarios_multiples_roles.sql"));
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT u.username, r.rol FROM usuarios u JOIN usuario_roles r ON r.usuario_id=u.id ORDER BY u.username")) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getString(2)).isEqualTo("ADMINISTRADOR");
            assertThat(rows.next()).isTrue(); assertThat(rows.getString(2)).isEqualTo("ANALISTA");
            assertThat(rows.next()).isTrue(); assertThat(rows.getString(2)).isEqualTo("NOTIFICADOR");
            assertThat(rows.next()).isFalse();
        }
        // La transaccion de prueba revierte el esquema y sus datos.
    }

    @Test void exigeAlMenosUnRolYNoPermiteRolesDesconocidos() throws Exception {
        for (String roles : new String[]{"[]", "[null]", "[\"DESCONOCIDO\"]"}) {
            mvc.perform(post("/api/usuarios").with(user("admin-test").roles("ADMINISTRADOR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"sinroles\",\"password\":\"prueba123\",\"roles\":" + roles + "}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(usuarios.existsByUsername("sinroles")).isFalse();
    }

    @Test void duplicadoDevuelve409YSinSesion401() throws Exception {
        crear("duplicado-test", Rol.ANALISTA);
        mvc.perform(post("/api/usuarios").with(user("admin-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"duplicado-test\",\"password\":\"prueba123\",\"roles\":[\"ANALISTA\"]}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test void inexistenteDevuelve404AlConsultarYEditar() throws Exception {
        mvc.perform(get("/api/usuarios/99999999").with(user("admin-test").roles("ADMINISTRADOR")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(put("/api/usuarios/99999999").with(user("admin-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"fantasma\",\"roles\":[\"ANALISTA\"],\"activo\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test void renombrarAUnUsuarioExistenteDevuelve409YConservarElPropioNombreNo() throws Exception {
        crear("ocupado-test", Rol.ANALISTA);
        Usuario propio = crear("propio-test", Rol.ANALISTA);

        mvc.perform(put("/api/usuarios/" + propio.getId()).with(user("admin-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\" ocupado-test \",\"roles\":[\"ANALISTA\"],\"activo\":true}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/usuarios/" + propio.getId()).with(user("admin-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"propio-test\",\"roles\":[\"NOTIFICADOR\"],\"activo\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("NOTIFICADOR"));
    }

    @Test void rechazaCamposInvalidosSinCrearNiModificar() throws Exception {
        for (String contenido : new String[]{"{}",
                "{\"username\":\" \",\"password\":\"prueba123\",\"roles\":[\"ANALISTA\"]}",
                "{\"username\":\"" + "a".repeat(256) + "\",\"password\":\"prueba123\",\"roles\":[\"ANALISTA\"]}",
                "{\"username\":\"corta-test\",\"password\":\"1234567\",\"roles\":[\"ANALISTA\"]}",
                "{\"username\":\"larga-test\",\"password\":\"" + "a".repeat(73) + "\",\"roles\":[\"ANALISTA\"]}"}) {
            mvc.perform(post("/api/usuarios").with(user("admin-test").roles("ADMINISTRADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(contenido))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores").isMap());
        }
        assertThat(usuarios.existsByUsername("corta-test")).isFalse();
        assertThat(usuarios.existsByUsername("larga-test")).isFalse();

        Usuario existente = crear("editar-test", Rol.ANALISTA);
        for (String contenido : new String[]{"{\"username\":\"editar-test\",\"roles\":[],\"activo\":true}",
                "{\"username\":\"editar-test\",\"roles\":[\"ANALISTA\"]}"}) {
            mvc.perform(put("/api/usuarios/" + existente.getId()).with(user("admin-test").roles("ADMINISTRADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(contenido))
                    .andExpect(status().isBadRequest());
        }
        entityManager.clear();
        assertThat(usuarios.findById(existente.getId()).orElseThrow().getRoles()).containsExactly(Rol.ANALISTA);
    }

    String edicion(String username, String roles, boolean activo) {
        return "{\"username\":\"" + username + "\",\"roles\":" + roles + ",\"activo\":" + activo + "}";
    }

    @Test void administradorNoSeQuitaSuRolNiSeDesactiva() throws Exception {
        Usuario yo = crear("yo-admin-test", Rol.ADMINISTRADOR, Rol.NOTIFICADOR);
        for (String contenido : new String[]{edicion("yo-admin-test", "[\"NOTIFICADOR\"]", true),
                edicion("yo-admin-test", "[\"ADMINISTRADOR\",\"NOTIFICADOR\"]", false)}) {
            mvc.perform(put("/api/usuarios/" + yo.getId()).with(user("yo-admin-test").roles("ADMINISTRADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(contenido))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("No podés quitarte el rol de administrador ni desactivar tu propia cuenta."));
        }
        entityManager.flush(); entityManager.clear();
        Usuario guardado = usuarios.findById(yo.getId()).orElseThrow();
        assertThat(guardado.getRoles()).contains(Rol.ADMINISTRADOR);
        assertThat(guardado.getActivo()).isTrue();
    }

    @Test void administradorEditaSuPropiaCuentaSinPerderElRol() throws Exception {
        Usuario yo = crear("yo-edito-test", Rol.ADMINISTRADOR);
        mvc.perform(put("/api/usuarios/" + yo.getId()).with(user("yo-edito-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edicion("yo-edito-test", "[\"ADMINISTRADOR\",\"ANALISTA\"]", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles", hasItems("ADMINISTRADOR", "ANALISTA")));
    }

    @Test void administradorQuitaElRolAOtroAdministrador() throws Exception {
        crear("primer-admin-test", Rol.ADMINISTRADOR);
        Usuario otro = crear("segundo-admin-test", Rol.ADMINISTRADOR);
        mvc.perform(put("/api/usuarios/" + otro.getId()).with(user("primer-admin-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(edicion("segundo-admin-test", "[\"ANALISTA\"]", true)))
                .andExpect(status().isOk());
    }

    @Test void noSeDejaAlSistemaSinAdministradorActivo() {
        Usuario ultimo = crear("ultimo-admin-test", Rol.ADMINISTRADOR);
        // Deja a este usuario como único administrador activo dentro de la transacción de prueba.
        jdbc.update("UPDATE usuarios SET activo = false WHERE id <> ? AND id IN "
                + "(SELECT usuario_id FROM usuario_roles WHERE rol = 'ADMINISTRADOR')", ultimo.getId());
        entityManager.clear();

        var quitarRol = new com.intendencia.gestion_morosidad_api.modules.usuario.dto.UsuarioUpdateRequest(
                "ultimo-admin-test", Set.of(Rol.ANALISTA), true);
        var desactivar = new com.intendencia.gestion_morosidad_api.modules.usuario.dto.UsuarioUpdateRequest(
                "ultimo-admin-test", Set.of(Rol.ADMINISTRADOR), false);
        for (var request : java.util.List.of(quitarRol, desactivar)) {
            assertThatThrownBy(() -> servicio.updateUser(ultimo.getId(), request, "otro-usuario"))
                    .isInstanceOf(com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException.class)
                    .hasMessage("Debe quedar al menos un administrador activo.");
        }
        assertThat(usuarios.contarActivosConRol(Rol.ADMINISTRADOR)).isEqualTo(1);
    }

    @Test void nombreDeUsuarioEsUnicoSinDistinguirMayusculasNiEspacios() throws Exception {
        crear("mayusculas-test", Rol.ANALISTA);
        Usuario otro = crear("otro-nombre-test", Rol.ANALISTA);

        mvc.perform(post("/api/usuarios").with(user("admin-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\" Mayusculas-TEST \",\"password\":\"prueba123\",\"roles\":[\"ANALISTA\"]}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/usuarios/" + otro.getId()).with(user("admin-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(edicion("MAYUSCULAS-test", "[\"ANALISTA\"]", true)))
                .andExpect(status().isConflict());
        // Cambiar solo las mayúsculas del propio nombre no choca consigo mismo.
        mvc.perform(put("/api/usuarios/" + otro.getId()).with(user("admin-test").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(edicion("Otro-Nombre-Test", "[\"ANALISTA\"]", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("Otro-Nombre-Test"));
    }
}
