package com.intendencia.gestion_morosidad_api.modules.captura;
import static com.intendencia.gestion_morosidad_api.modules.captura.CapturaDtos.*;
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
@WebMvcTest(CapturaController.class)
@Import(SecurityConfig.class)
class CapturaControllerTest {
 @Autowired MockMvc mvc;
 @MockitoBean CapturaService service;
 @MockitoBean JwtDecoder decoder;
 @MockitoBean JwtAuthenticationConverter converter;
 @ParameterizedTest @ValueSource(strings={"ADMINISTRADOR","NOTIFICADOR","ANALISTA"})
 void guardaConAutorDeSesionYPermiteLeer(String rol) throws Exception {
  var detalle=new Detalle(1L,"Prueba",LocalDateTime.of(2026,11,3,12,0),"usuario",List.of());
  when(service.crear(any(),eq("usuario"))).thenReturn(detalle); when(service.obtener(1L)).thenReturn(detalle);
  mvc.perform(post("/api/capturas-cartera").with(user("usuario").roles(rol)).contentType("application/json").content("""
   {"nombre":"Prueba","cuentas":[{"cm":"1001","origen":"SUCIVE"}]}
   """)).andExpect(status().isCreated()).andExpect(header().string("Location","/api/capturas-cartera/1")).andExpect(jsonPath("$.usuario").value("usuario"));
  mvc.perform(get("/api/capturas-cartera/1").with(user("usuario").roles(rol))).andExpect(status().isOk());
  mvc.perform(get("/api/capturas-cartera").with(user("usuario").roles(rol))).andExpect(status().isOk());
 }
 @Test void noPermiteCapturaSinCuentas() throws Exception {
  mvc.perform(post("/api/capturas-cartera").with(user("a").roles("ANALISTA")).contentType("application/json").content("{\"nombre\":\"X\",\"cuentas\":[]}")).andExpect(status().isBadRequest()); verifyNoInteractions(service);
 }
 @Test void requiereSesion() throws Exception { mvc.perform(get("/api/capturas-cartera")).andExpect(status().isUnauthorized()); }
}
