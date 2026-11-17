package com.intendencia.gestion_morosidad_api.modules.sincronizacion.controller;
import com.intendencia.gestion_morosidad_api.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.service.SincronizacionDeudasService;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import java.util.List;
@WebMvcTest(SincronizacionController.class) @Import(SecurityConfig.class) @WithMockUser(roles="ADMINISTRADOR")
class SincronizacionControllerTest {
 @Autowired MockMvc mvc;@MockitoBean JwtDecoder decoder;@MockitoBean JwtAuthenticationConverter converter;
 @MockitoBean SincronizacionDeudasService service;
 @Test void origenPredeterminadoYResumenDeRespuesta() throws Exception {var inicio=java.time.LocalDateTime.of(2026,11,17,12,0);when(service.sincronizar()).thenReturn(new ResultadoSincronizacion(inicio,inicio.plusSeconds(5),10,2,1,9,0,0,List.of()));mvc.perform(post("/api/sincronizaciones/deudas")).andExpect(status().isOk()).andExpect(jsonPath("$.pendientesRecibidas").value(10)).andExpect(jsonPath("$.deudasActualizadas").value(9)).andExpect(jsonPath("$.advertencias.length()").value(0));verify(service).sincronizar();}
 @Test void suciveUsaSuProveedor() throws Exception {mvc.perform(post("/api/sincronizaciones/deudas").param("origen","SUCIVE")).andExpect(status().isOk());verify(service).sincronizar(OrigenDatos.SUCIVE);verify(service,never()).sincronizar();}
 @Test void origenInvalidoEs400() throws Exception {mvc.perform(post("/api/sincronizaciones/deudas").param("origen","INVALIDO")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void ejecucionSolapadaEs409() throws Exception {when(service.sincronizar()).thenThrow(new OperacionEnCursoException("Ya hay una sincronización en curso"));mvc.perform(post("/api/sincronizaciones/deudas")).andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));}
 @Test void falloExternoEs502SinStacktrace() throws Exception {when(service.sincronizar()).thenThrow(new IntegracionExternaException("GeoPat respondió HTTP 504"));mvc.perform(post("/api/sincronizaciones/deudas")).andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value(502)).andExpect(jsonPath("$.trace").doesNotExist());}
}
