package com.intendencia.gestion_morosidad_api.modules.deuda.controller;
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
import com.intendencia.gestion_morosidad_api.modules.deuda.dto.*;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.CarteraAgrupadaService;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.util.*;
import org.springframework.data.domain.Pageable;
import static org.assertj.core.api.Assertions.*;
@WebMvcTest(CarteraAgrupadaController.class) @Import(SecurityConfig.class) @WithMockUser(roles="ANALISTA")
class CarteraAgrupadaControllerTest {
 @Autowired MockMvc mvc;@MockitoBean JwtDecoder decoder;@MockitoBean JwtAuthenticationConverter converter;
 @MockitoBean CarteraAgrupadaService service;
 @Test void paginaMaximaEs100YTransfiereTodosLosFiltros() throws Exception {
  when(service.listar(any(),any())).thenReturn(new PaginaResponse<>(List.of(),2,100,250,3));
  mvc.perform(get("/api/deudas/agrupadas").param("page","2").param("size","100000").param("contribuyente","001").param("origen","SUCIVE").param("tipoPadron","PAT").param("conContacto","true").param("sort","importe,desc"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.pagina").value(2)).andExpect(jsonPath("$.tamanio").value(100)).andExpect(jsonPath("$.totalElementos").value(250));
  verify(service).listar(eq(new DeudaFiltro(null,null,"001",null,null,null,true,"PAT",OrigenDatos.SUCIVE)),argThat((Pageable p)->p.getPageSize()==100&&p.getPageNumber()==2&&p.getSort().getOrderFor("importe").isDescending()));
 }
 @Test void respuestaAgrupadaIncluyeDocumentoTotalYDeudasCompletas() throws Exception {
  var g=new DeudasAgrupadasResponse("DOC:001","001",List.of("Persona"),new java.math.BigDecimal("30.50"),10L,null,List.of(DeudaResponse.builder().id(1L).build(),DeudaResponse.builder().id(2L).build()));
  when(service.listar(any(),any())).thenReturn(new PaginaResponse<>(List.of(g),0,20,1,1));
  mvc.perform(get("/api/deudas/agrupadas")).andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].documento").value("001")).andExpect(jsonPath("$.contenido[0].importeTotal").value(30.5)).andExpect(jsonPath("$.contenido[0].deudas.length()").value(2));
 }
 @ParameterizedTest @ValueSource(strings={"origen","estado","conContacto","segmento"}) void parametroInvalidoEs400(String campo) throws Exception {mvc.perform(get("/api/deudas/agrupadas").param(campo,"INVALIDO")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void normalizaPaginaNegativaYTamanoCero() throws Exception {when(service.listar(any(),any())).thenReturn(new PaginaResponse<>(List.of(),0,20,0,0));mvc.perform(get("/api/deudas/agrupadas").param("page","-1").param("size","0")).andExpect(status().isOk());verify(service).listar(any(),argThat((Pageable p)->p.getPageNumber()==0&&p.getPageSize()==20));}
 @Test void exigeSesion() throws Exception {mvc.perform(get("/api/deudas/agrupadas").with(anonymous())).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
}
