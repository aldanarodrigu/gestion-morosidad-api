package com.intendencia.gestion_morosidad_api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PerfilProdErroresTest.Sonda.class)
@Import({SecurityConfig.class, PerfilProdErroresTest.Sonda.class})
@ActiveProfiles("prod")
@WithMockUser(roles="ADMINISTRADOR")
class PerfilProdErroresTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;
    @RestController static class Sonda {
        record Datos(@NotBlank String nombre) {}
        @GetMapping("/api/qa-prod/inesperado") String inesperado() {
            throw new IllegalStateException("SQL_INTERNO_NO_PUBLICAR jdbc:postgresql://servidor-privado/base");
        }
        @PostMapping("/api/qa-prod/datos") Datos datos(@Valid @RequestBody Datos datos) {return datos;}
    }
    @Test void error500NoExponeExcepcionNiConexionNiStacktrace() throws Exception {
        mvc.perform(get("/api/qa-prod/inesperado")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.mensaje").value("Ocurrió un error inesperado"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().string(not(containsString("SQL_INTERNO_NO_PUBLICAR"))))
                .andExpect(content().string(not(containsString("jdbc:"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
    @Test void validacion400NoIncluyeElCuerpoOriginal() throws Exception {
        mvc.perform(post("/api/qa-prod/datos").contentType("application/json").content("{\"nombre\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.nombre").exists())
                .andExpect(jsonPath("$.trace").doesNotExist()).andExpect(jsonPath("$.exception").doesNotExist());
    }
    @Test void jsonInvalidoNoDevuelveDetallesDelParser() throws Exception {
        mvc.perform(post("/api/qa-prod/datos").contentType("application/json").content("{TOKEN_PRIVADO_NO_PUBLICAR"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("TOKEN_PRIVADO_NO_PUBLICAR"))))
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la solicitud no es válido"));
    }
    @Test void rutaInexistenteDevuelve404SinDetallesInternos() throws Exception {
        mvc.perform(get("/api/qa-prod/no-existe"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.trace").doesNotExist());
    }
    @Test void rutaProtegidaSinSesionDevuelve401() throws Exception {
        mvc.perform(get("/api/qa-prod/inesperado").with(anonymous())).andExpect(status().isUnauthorized());
    }
}
