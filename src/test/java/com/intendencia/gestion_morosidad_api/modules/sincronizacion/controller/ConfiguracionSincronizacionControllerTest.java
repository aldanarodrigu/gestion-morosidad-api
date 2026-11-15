package com.intendencia.gestion_morosidad_api.modules.sincronizacion.controller;
import com.intendencia.gestion_morosidad_api.config.SecurityConfig;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.service.*;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(ConfiguracionSincronizacionController.class) @Import(SecurityConfig.class)
class ConfiguracionSincronizacionControllerTest {
 @Autowired MockMvc mvc; @MockitoBean PeriodoSincronizacionService periodo; @MockitoBean SincronizacionManualService manual;
 @MockitoBean JwtDecoder decoder; @MockitoBean JwtAuthenticationConverter converter;
 @Test void soloAdministradorConfiguraYEjecuta() throws Exception {
  for(String rol:new String[]{"NOTIFICADOR","ANALISTA"}) {
   mvc.perform(get("/api/sincronizaciones/configuracion").with(user("u").roles(rol))).andExpect(status().isForbidden());
   mvc.perform(post("/api/sincronizaciones/ejecucion").with(user("u").roles(rol))).andExpect(status().isForbidden());
   mvc.perform(post("/api/sincronizaciones/ejecucion/cancelar").with(user("u").roles(rol))).andExpect(status().isForbidden());
   mvc.perform(put("/api/sincronizaciones/configuracion").with(user("u").roles(rol)).contentType("application/json").content("{\"anioMinimo\":2013}")).andExpect(status().isForbidden());
  }
  mvc.perform(get("/api/sincronizaciones/configuracion")).andExpect(status().isUnauthorized()); verifyNoInteractions(periodo,manual);
 }
 @Test void administradorGuardaYDisparaOrigenCorrecto() throws Exception {
  when(periodo.anioMinimo()).thenReturn(2013);
  mvc.perform(put("/api/sincronizaciones/configuracion").with(user("a").roles("ADMINISTRADOR")).contentType("application/json").content("{\"anioMinimo\":2013}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.anioMinimo").value(2013));verify(manual).guardar(2013);
  mvc.perform(post("/api/sincronizaciones/ejecucion").param("origen","SUCIVE").with(user("a").roles("ADMINISTRADOR"))).andExpect(status().isAccepted());verify(manual).iniciar(OrigenDatos.SUCIVE);
  mvc.perform(get("/api/sincronizaciones/ejecucion").with(user("a").roles("ADMINISTRADOR"))).andExpect(status().isNoContent());
 }
 @Test void anioAusenteSeRechaza() throws Exception {
  mvc.perform(put("/api/sincronizaciones/configuracion").with(user("a").roles("ADMINISTRADOR")).contentType("application/json").content("{}"))
   .andExpect(status().isBadRequest());verifyNoInteractions(manual);
 }
 @Test void exponeAvanceValidadoSinInventarUnTotal() throws Exception {
  var p = new com.intendencia.gestion_morosidad_api.integration.intendencia.common.ProgresoSincronizacion("DEUDAS",55,56,55000,null,null,3,"Reintentando página 56");
  when(manual.estado()).thenReturn(new SincronizacionManualService.Ejecucion("EN_CURSO",OrigenDatos.SUCIVE,2013,java.time.LocalDateTime.of(2026,11,12,12,0),null,p.detalle(),null,p));
  mvc.perform(get("/api/sincronizaciones/ejecucion").with(user("a").roles("ADMINISTRADOR")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.progreso.etapa").value("DEUDAS"))
   .andExpect(jsonPath("$.progreso.paginasDescargadas").value(55)).andExpect(jsonPath("$.progreso.paginaActual").value(56))
   .andExpect(jsonPath("$.progreso.registrosDescargados").value(55000)).andExpect(jsonPath("$.progreso.intento").value(3))
   .andExpect(jsonPath("$.progreso.totalRegistros").value(org.hamcrest.Matchers.nullValue()));
 }

 @Test void administradorPuedeSolicitarCancelacion() throws Exception {
  when(manual.cancelar()).thenReturn(new SincronizacionManualService.Ejecucion("CANCELANDO",OrigenDatos.SUCIVE,2013,java.time.LocalDateTime.now(),null,"Cancelando",null));
  mvc.perform(post("/api/sincronizaciones/ejecucion/cancelar").with(user("a").roles("ADMINISTRADOR")))
   .andExpect(status().isAccepted()).andExpect(jsonPath("$.estado").value("CANCELANDO"));verify(manual).cancelar();
 }

}
