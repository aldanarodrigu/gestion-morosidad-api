package com.intendencia.gestion_morosidad_api.shared.exception;

import com.intendencia.gestion_morosidad_api.config.SecurityConfig;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.controller.SincronizacionController;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.service.SincronizacionDeudasService;
import com.intendencia.gestion_morosidad_api.modules.tributo.controller.TributoController;
import com.intendencia.gestion_morosidad_api.modules.tributo.service.TributoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Errores de protocolo HTTP: deben responder con su código (400/404/405/415) y el JSON de ErrorResponse,
 * nunca como 500 "error inesperado".
 */
@WebMvcTest({TributoController.class, SincronizacionController.class})
@Import(SecurityConfig.class)
class ErroresHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean TributoService tributos;
    @MockitoBean SincronizacionDeudasService sincronizacion;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;

    ResultActions esError(ResultActions resultado, int estado) throws Exception {
        return resultado.andExpect(status().is(estado))
                .andExpect(jsonPath("$.status").value(estado))
                .andExpect(jsonPath("$.mensaje").isString())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test void idNoNumericoDevuelve400() throws Exception {
        esError(mvc.perform(put("/api/tributos/abc").with(user("admin").roles("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"Nueva\"}")), 400);
    }

    @Test void enumInvalidoEnParametroDevuelve400() throws Exception {
        esError(mvc.perform(post("/api/sincronizaciones/deudas?origen=OTRO").with(user("admin").roles("ADMINISTRADOR"))), 400);
    }

    @Test void jsonMalFormadoOCuerpoVacioDevuelve400() throws Exception {
        esError(mvc.perform(put("/api/tributos/1").with(user("admin").roles("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":")), 400);
        esError(mvc.perform(put("/api/tributos/1").with(user("admin").roles("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON)), 400);
    }

    @Test void rutaInexistenteDevuelve404() throws Exception {
        esError(mvc.perform(get("/api/no-existe").with(user("admin").roles("ADMINISTRADOR"))), 404);
    }

    @Test void metodoNoSoportadoDevuelve405() throws Exception {
        esError(mvc.perform(patch("/api/tributos/1").with(user("admin").roles("ADMINISTRADOR"))), 405)
                .andExpect(header().string("Allow", containsString("PUT")));
    }

    @Test void tipoDeContenidoNoSoportadoDevuelve415() throws Exception {
        esError(mvc.perform(post("/api/tributos").with(user("admin").roles("ADMINISTRADOR"))
                .contentType(MediaType.TEXT_PLAIN).content("codigo=T1")), 415);
    }
}
