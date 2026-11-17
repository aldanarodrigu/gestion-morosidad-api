package com.intendencia.gestion_morosidad_api.modules.segmento.controller;

import com.intendencia.gestion_morosidad_api.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.intendencia.gestion_morosidad_api.modules.segmento.dto.ConfiguracionSegmentoResponse;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraResponse;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ConfiguracionSegmentoService;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.SegmentoMoraService;
import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({SegmentoMoraController.class, ConfiguracionSegmentoController.class})
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMINISTRADOR")
class SegmentoControllersTest {
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SegmentoMoraService segmentoMoraService;
    @MockitoBean
    private ConfiguracionSegmentoService configuracionService;

    @Test
    void listarSegmentos() throws Exception {
        when(segmentoMoraService.listar()).thenReturn(List.of(new SegmentoMoraResponse(1L, "Mora Temprana", null, 1)));

        mockMvc.perform(get("/api/segmentos-mora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Mora Temprana"));
    }

    @Test
    void crearSegmentoDevuelve201() throws Exception {
        when(segmentoMoraService.crear(any())).thenReturn(new SegmentoMoraResponse(4L, "Mora Judicial", null, 4));

        mockMvc.perform(post("/api/segmentos-mora")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Mora Judicial\",\"orden\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4));
    }

    @Test
    void crearSegmentoSinNombreDevuelve400() throws Exception {
        mockMvc.perform(post("/api/segmentos-mora")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orden\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    @Test
    void listarConfiguracionesConHistorial() throws Exception {
        when(configuracionService.listar(true)).thenReturn(List.of(
                new ConfiguracionSegmentoResponse(1L, 1L, "Mora Temprana", 0, 90, false, null, null)));

        mockMvc.perform(get("/api/configuraciones-segmento").param("historial", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activa").value(false));
    }

    @Test
    void crearConfiguracionSuperpuestaDevuelve400ConMotivo() throws Exception {
        when(configuracionService.crear(any()))
                .thenThrow(new ReglaNegocioException("El rango se superpone con el de 'Mora Inicial'"));

        mockMvc.perform(post("/api/configuraciones-segmento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmentoId\":1,\"diasDesde\":0,\"diasHasta\":120}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El rango se superpone con el de 'Mora Inicial'"));
    }

    @Test
    void crearConfiguracionConDiasNegativosDevuelve400() throws Exception {
        mockMvc.perform(post("/api/configuraciones-segmento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmentoId\":1,\"diasDesde\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.diasDesde").exists());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"NOTIFICADOR","ANALISTA"})
    void otrosRolesConsultanPeroNoCreanConfiguraciones(String rol) throws Exception {
        var usuario=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("lector").roles(rol);
        mockMvc.perform(get("/api/segmentos-mora").with(usuario)).andExpect(status().isOk());
        mockMvc.perform(get("/api/configuraciones-segmento").with(usuario)).andExpect(status().isOk());
        mockMvc.perform(post("/api/configuraciones-segmento").with(usuario).contentType(MediaType.APPLICATION_JSON)
                .content("{\"segmentoId\":1,\"diasDesde\":0,\"diasHasta\":90}"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verify(configuracionService,org.mockito.Mockito.never()).crear(any());
    }
    @Test void unUsuarioSinRolDelSistemaNoPuedeConsultar() throws Exception {
        mockMvc.perform(get("/api/segmentos-mora").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ajeno").roles("USER")))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(segmentoMoraService,configuracionService);
    }
    @Test void exigeAutenticacionParaConsultar() throws Exception {
        mockMvc.perform(get("/api/configuraciones-segmento").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(configuracionService);
    }
}
