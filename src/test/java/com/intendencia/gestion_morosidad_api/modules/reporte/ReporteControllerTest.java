package com.intendencia.gestion_morosidad_api.modules.reporte;

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
import java.util.List;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(ReporteController.class)
@Import(SecurityConfig.class)
class ReporteControllerTest {
 @Autowired MockMvc mvc;
 @MockitoBean ReporteService service;
 @MockitoBean JwtDecoder decoder;
 @MockitoBean JwtAuthenticationConverter converter;
 @ParameterizedTest @ValueSource(strings={"ADMINISTRADOR","NOTIFICADOR","ANALISTA"})
 void permiteConsultarYExportarConFiltros(String rol) throws Exception {
  when(service.exportar(com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos.SUCIVE,"PAT",3L)).thenReturn(new byte[]{80,75});
  mvc.perform(get("/api/reportes/cartera").with(user("usuario").roles(rol)).param("origen","SUCIVE").param("tipoPadron","PAT").param("segmento","3")).andExpect(status().isOk());
  verify(service).consultar(com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos.SUCIVE,"PAT",3L);
  mvc.perform(get("/api/reportes/cartera/exportar").with(user("usuario").roles(rol)).param("origen","SUCIVE").param("tipoPadron","PAT").param("segmento","3")).andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=reporte-cartera.xlsx"));
 }
 @Test void exigeAutenticacion() throws Exception {mvc.perform(get("/api/reportes/cartera")).andExpect(status().isUnauthorized());mvc.perform(get("/api/reportes/cartera/exportar")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
 @Test void rechazaOrigenInvalido() throws Exception {mvc.perform(get("/api/reportes/cartera").with(user("a").roles("ANALISTA")).param("origen","OTRO")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
}
