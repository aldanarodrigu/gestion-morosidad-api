package com.intendencia.gestion_morosidad_api.modules.deuda.controller;

import com.intendencia.gestion_morosidad_api.config.SecurityConfig;
import com.intendencia.gestion_morosidad_api.modules.deuda.dto.FacturasPadronResponse;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.FacturasPadronService;
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

@WebMvcTest(FacturasPadronController.class)
@Import(SecurityConfig.class)
class FacturasPadronControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean FacturasPadronService servicio;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "NOTIFICADOR", "ANALISTA"})
    void todosLosRolesConsultanFacturas(String rol) throws Exception {
        when(servicio.consultar(42L,"PENDIENTE",true,false)).thenReturn(new FacturasPadronResponse(java.util.List.of(),new FacturasPadronResponse.Meta(0L)));
        mvc.perform(get("/api/deudas/42/facturas").with(user("usuario").roles(rol))
                .param("estado","PENDIENTE").param("vencida","true").param("incluirConvenios","false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(0));
        verify(servicio).consultar(42L,"PENDIENTE",true,false);
    }
    @Test void exigeAutenticacion() throws Exception {
        mvc.perform(get("/api/deudas/42/facturas")).andExpect(status().isUnauthorized()); verifyNoInteractions(servicio);
    }
}
