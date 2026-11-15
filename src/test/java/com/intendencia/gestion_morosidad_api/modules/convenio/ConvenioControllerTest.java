package com.intendencia.gestion_morosidad_api.modules.convenio;

import com.intendencia.gestion_morosidad_api.config.SecurityConfig;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiMeta;

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

@WebMvcTest(ConvenioController.class)
@Import(SecurityConfig.class)
class ConvenioControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConvenioService servicio;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "NOTIFICADOR", "ANALISTA"})
    void todosLosRolesConsultanFacturas(String rol) throws Exception {
        when(servicio.consultar(OrigenDatos.SUCIVE,2,20,900L,null,null,null,null)).thenReturn(new IntendenciaApiResponse<>(java.util.List.of(),new IntendenciaApiMeta(0L,null)));
        mvc.perform(get("/api/convenios").with(user("usuario").roles(rol))
                .param("origen","SUCIVE").param("pagina","2").param("cm","900"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(0));
        verify(servicio).consultar(OrigenDatos.SUCIVE,2,20,900L,null,null,null,null);
    }
    @Test void exigeAutenticacion() throws Exception {
        mvc.perform(get("/api/convenios")).andExpect(status().isUnauthorized()); verifyNoInteractions(servicio);
    }
}
