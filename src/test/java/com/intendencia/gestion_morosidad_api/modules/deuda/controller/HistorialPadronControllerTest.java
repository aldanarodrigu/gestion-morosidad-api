package com.intendencia.gestion_morosidad_api.modules.deuda.controller;

import com.intendencia.gestion_morosidad_api.config.SecurityConfig;
import com.intendencia.gestion_morosidad_api.modules.deuda.dto.HistorialPadronResponse;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.HistorialPadronService;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HistorialPadronController.class)
@Import(SecurityConfig.class)
class HistorialPadronControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HistorialPadronService servicio;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "NOTIFICADOR", "ANALISTA"})
    void todosLosRolesConsultanFacturas(String rol) throws Exception {
        when(servicio.consultar(42L)).thenReturn(new HistorialPadronResponse(java.util.List.of(),new HistorialPadronResponse.Meta(0L)));
        mvc.perform(get("/api/deudas/42/historial-contribuyentes").with(user("usuario").roles(rol)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(0));
        verify(servicio).consultar(42L);
    }
    @Test void exigeAutenticacion() throws Exception {
        mvc.perform(get("/api/deudas/42/historial-contribuyentes")).andExpect(status().isUnauthorized()); verifyNoInteractions(servicio);
    }
}
