package com.intendencia.gestion_morosidad_api.config;

import com.intendencia.gestion_morosidad_api.modules.auth.AuthController;
import com.intendencia.gestion_morosidad_api.modules.auth.AuthService;
import com.intendencia.gestion_morosidad_api.modules.auth.CustomUserDetailsService;
import com.intendencia.gestion_morosidad_api.modules.auth.TokenService;
import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import com.intendencia.gestion_morosidad_api.modules.usuario.Usuario;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioController;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Validación de tokens con la configuración real (JwtConfig + SecurityConfig): firma HS256, vencimiento,
 * y roles/estado leídos de la base en cada request para que las revocaciones apliquen a tokens ya emitidos.
 */
@WebMvcTest({AuthController.class, UsuarioController.class})
// CustomUserDetailsService completa el AuthenticationManager global como en la app: sin él, el rechazo
// de un JWT delega en ese manager vacío y entra en recursión infinita.
@Import({SecurityConfig.class, JwtConfig.class, TokenService.class, CustomUserDetailsService.class})
@TestPropertySource(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration-seconds=3600"})
class AutenticacionJwtTest {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired TokenService tokens;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean UsuarioService usuarioService;
    @MockitoBean AuthService authService;

    Usuario usuario(String username, boolean activo, Rol... roles) {
        var usuario = new Usuario(1L, username, "hash", EnumSet.copyOf(List.of(roles)), activo);
        when(usuarios.findByUsername(username)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    String token(JwtEncoder conEncoder, String subject, Instant emitido, Instant vence, Rol... roles) {
        var claims = JwtClaimsSet.builder().issuedAt(emitido).expiresAt(vence)
                .claim("roles", List.of(roles).stream().map(Enum::name).toList());
        if (subject != null) claims.subject(subject);
        return conEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    String tokenVigente(String subject, Rol... roles) {
        Instant ahora = Instant.now();
        return token(encoder, subject, ahora, ahora.plus(Duration.ofHours(1)), roles);
    }

    static String bearer(String token) { return "Bearer " + token; }

    @Test void tokenVigenteAccedeASuSesion() throws Exception {
        usuario("ana", true, Rol.ANALISTA);

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(tokenVigente("ana", Rol.ANALISTA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana"));
    }

    @Test void tokenEmitidoPorTokenServiceEsAceptadoYVenceSegunConfiguracion() throws Exception {
        var ana = usuario("ana", true, Rol.ADMINISTRADOR, Rol.NOTIFICADOR);

        String token = tokens.generateToken(ana);
        var jwt = decoder.decode(token);

        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofSeconds(3600));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMINISTRADOR", "NOTIFICADOR");
        mvc.perform(get("/api/usuarios").header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    @Test void sinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test void tokenVencidoDevuelve401() throws Exception {
        usuario("ana", true, Rol.ADMINISTRADOR);
        // Supera la tolerancia de reloj de 60 s del validador.
        Instant ahora = Instant.now();
        String vencido = token(encoder, "ana", ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofMinutes(5)), Rol.ADMINISTRADOR);

        mvc.perform(get("/api/usuarios").header("Authorization", bearer(vencido)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")));
    }

    @Test void tokenFirmadoConOtraClaveDevuelve401() throws Exception {
        usuario("ana", true, Rol.ADMINISTRADOR);
        var otraClave = new SecretKeySpec("otra-clave-de-32-bytes-distinta!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var otroEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(otraClave));
        Instant ahora = Instant.now();

        mvc.perform(get("/api/usuarios").header("Authorization",
                        bearer(token(otroEncoder, "ana", ahora, ahora.plus(Duration.ofHours(1)), Rol.ADMINISTRADOR))))
                .andExpect(status().isUnauthorized());
    }

    @Test void tokenConPayloadAlteradoDevuelve401() throws Exception {
        usuario("ana", true, Rol.ANALISTA);
        usuario("admin", true, Rol.ADMINISTRADOR);
        String[] partes = tokenVigente("ana", Rol.ANALISTA).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8).replace("\"ana\"", "\"admin\"");
        String alterado = partes[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + partes[2];

        mvc.perform(get("/api/usuarios").header("Authorization", bearer(alterado))).andExpect(status().isUnauthorized());
    }

    @Test void tokenSinFirmaConAlgNoneDevuelve401() throws Exception {
        usuario("admin", true, Rol.ADMINISTRADOR);
        var b64 = Base64.getUrlEncoder().withoutPadding();
        long vence = Instant.now().plus(Duration.ofHours(1)).getEpochSecond();
        String sinFirma = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(("{\"sub\":\"admin\",\"exp\":" + vence + "}").getBytes(StandardCharsets.UTF_8)) + ".";

        mvc.perform(get("/api/usuarios").header("Authorization", bearer(sinFirma))).andExpect(status().isUnauthorized());
    }

    @Test void tokenMalFormadoDevuelve401() throws Exception {
        for (String basura : new String[]{"abc", "a.b.c", "Bearer", ""}) {
            mvc.perform(get("/api/auth/me").header("Authorization", bearer(basura))).andExpect(status().isUnauthorized());
        }
    }

    @Test void tokenSinSubjectDevuelve401() throws Exception {
        Instant ahora = Instant.now();
        mvc.perform(get("/api/auth/me").header("Authorization",
                        bearer(token(encoder, null, ahora, ahora.plus(Duration.ofHours(1)), Rol.ADMINISTRADOR))))
                .andExpect(status().isUnauthorized());
    }

    @Test void cuentaDesactivadaNoUsaUnTokenYaEmitido() throws Exception {
        String token = tokenVigente("ana", Rol.ADMINISTRADOR);
        usuario("ana", false, Rol.ADMINISTRADOR);

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
    }

    @Test void usuarioEliminadoNoUsaUnTokenYaEmitido() throws Exception {
        String token = tokenVigente("borrado", Rol.ADMINISTRADOR);
        when(usuarios.findByUsername("borrado")).thenReturn(Optional.empty());

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
    }

    @Test void revocarUnRolAplicaAlMismoTokenAunqueElClaimLoIncluya() throws Exception {
        String token = tokenVigente("ana", Rol.ADMINISTRADOR);
        usuario("ana", true, Rol.ADMINISTRADOR);
        mvc.perform(get("/api/usuarios").header("Authorization", bearer(token))).andExpect(status().isOk());

        usuario("ana", true, Rol.ANALISTA);

        mvc.perform(get("/api/usuarios").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    @Test void elClaimRolesNoOtorgaPermisos() throws Exception {
        // Un token firmado que declara ADMINISTRADOR no alcanza si la base dice otra cosa.
        usuario("ana", true, Rol.NOTIFICADOR);

        mvc.perform(get("/api/usuarios").header("Authorization", bearer(tokenVigente("ana", Rol.ADMINISTRADOR))))
                .andExpect(status().isForbidden());
    }

    @Test void agregarUnRolAplicaAlMismoToken() throws Exception {
        String token = tokenVigente("ana", Rol.ANALISTA);
        usuario("ana", true, Rol.ANALISTA, Rol.ADMINISTRADOR);

        mvc.perform(get("/api/usuarios").header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    @Test void loginConCredencialesInvalidasDevuelve401SinDetalle() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos"));
    }

    @Test void loginFuncionaAunqueElNavegadorMandeUnTokenVencido() throws Exception {
        Instant ahora = Instant.now();
        String vencido = token(encoder, "ana", ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofMinutes(5)));

        mvc.perform(post("/api/auth/login").header("Authorization", bearer(vencido))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"clave-segura\"}"))
                .andExpect(status().isOk());
    }
}
