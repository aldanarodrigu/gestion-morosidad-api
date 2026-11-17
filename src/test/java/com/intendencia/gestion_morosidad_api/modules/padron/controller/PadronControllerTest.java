package com.intendencia.gestion_morosidad_api.modules.padron.controller;
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
import com.intendencia.gestion_morosidad_api.modules.padron.service.PadronService;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.util.List;
@WebMvcTest(PadronController.class) @Import(SecurityConfig.class) @WithMockUser(roles="ANALISTA")
class PadronControllerTest {
 @Autowired MockMvc mvc;@MockitoBean JwtDecoder decoder;@MockitoBean JwtAuthenticationConverter converter;
 @MockitoBean PadronService service;
 @Test void numeroRepetidoConservaListaYCmOrigenYTitular() throws Exception {var c=new ContribuyenteResponse("100","Persona","001",OrigenDatos.SUCIVE);when(service.buscarPorNumeroPadron("0007",null)).thenReturn(List.of(new PadronResponse("100","0007","PAT","Prueba",null,null,c,OrigenDatos.SUCIVE,"SAA1234")));mvc.perform(get("/api/padrones/0007")).andExpect(status().isOk()).andExpect(jsonPath("$[0].cm").value("100")).andExpect(jsonPath("$[0].origen").value("SUCIVE")).andExpect(jsonPath("$[0].contribuyente.documento").value("001")).andExpect(jsonPath("$[0].matricula").value("SAA1234"));}
 @Test void transfiereLocalidadEnConsultaDeTitular() throws Exception {when(service.buscarContribuyentes("0007","Libertad")).thenReturn(List.of(new ContribuyenteResponse("100","Persona","001")));mvc.perform(get("/api/padrones/0007/contribuyente").param("localidad","Libertad")).andExpect(status().isOk()).andExpect(jsonPath("$[0].documento").value("001"));verify(service).buscarContribuyentes("0007","Libertad");}
 @ParameterizedTest @ValueSource(strings={"","/contribuyente"}) void sinResultadosDevuelve404(String sufijo) throws Exception {mvc.perform(get("/api/padrones/NO-EXISTE"+sufijo)).andExpect(status().isNotFound());}
 @Test void requiereSesion() throws Exception {mvc.perform(get("/api/padrones/0007").with(anonymous())).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
}
