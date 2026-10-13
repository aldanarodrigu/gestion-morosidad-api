package com.intendencia.gestion_morosidad_api.modules.accion;

import com.intendencia.gestion_morosidad_api.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccionController.class)
@Import({AccionService.class, SecurityConfig.class})
class AccionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AccionRepository repository;
    @MockitoBean com.intendencia.gestion_morosidad_api.modules.accion_segmento.AccionSegmentoRepository vinculos;
    @MockitoBean com.intendencia.gestion_morosidad_api.modules.segmento.repository.SegmentoMoraRepository segmentos;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;

    private Accion accion() {
        Accion a = new Accion(); a.setId(1L); a.setNombre("Llamada");
        a.setActivo(true); return a;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "ANALISTA", "NOTIFICADOR"})
    void listaTodasLasAccionesInclusoInactivas(String rol) throws Exception {
        Accion a = accion(); a.setActivo(false);
        when(repository.findAll()).thenReturn(List.of(a));
        mvc.perform(get("/api/acciones").with(user("operador").roles(rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Llamada"))
                .andExpect(jsonPath("$[0].activo").value(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void administradorGuardaElEstadoSinModificarNombre(boolean activo) throws Exception {
        Accion a = accion(); a.setActivo(!activo);
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.save(a)).thenReturn(a);
        mvc.perform(put("/api/acciones/1").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":" + activo + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(activo));
        verify(repository).save(a);
        assertThat(a.getNombre()).isEqualTo("Llamada");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ANALISTA", "NOTIFICADOR"})
    void otrosRolesNoPuedenModificarCrearNiEliminarAcciones(String rol) throws Exception {
        mvc.perform(put("/api/acciones/1").with(user("operador").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/acciones").with(user("operador").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Nueva\",\"segmentoId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/acciones/1").with(user("operador").roles(rol)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test void sinAutenticarDevuelve401() throws Exception {
        mvc.perform(get("/api/acciones")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/acciones/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":false}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test void estadoNuloDevuelve400SinGuardar() throws Exception {
        mvc.perform(put("/api/acciones/1").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test void inexistenteDevuelve404() throws Exception {
        mvc.perform(put("/api/acciones/99").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isNotFound());
        verify(repository, never()).save(any());
    }

}
