package com.intendencia.gestion_morosidad_api.modules.usuario;
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
import com.intendencia.gestion_morosidad_api.modules.usuario.dto.*;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import java.util.*;
@WebMvcTest(UsuarioController.class) @Import(SecurityConfig.class) @WithMockUser(username="admin-prueba",roles="ADMINISTRADOR")
class UsuarioControllerTest {
 @Autowired MockMvc mvc;@MockitoBean JwtDecoder decoder;@MockitoBean JwtAuthenticationConverter converter;
 @MockitoBean UsuarioService service;
 String cuerpo="{\"username\":\"nuevo\",\"password\":\"clave-prueba\",\"roles\":[\"ADMINISTRADOR\",\"NOTIFICADOR\",\"ANALISTA\"]}";
 @Test void altaDevuelve201RolesYNoPassword() throws Exception {when(service.saveUser(any())).thenReturn(new UsuarioResponse(1L,"nuevo",Set.of(Rol.values()),true));mvc.perform(post("/api/usuarios").contentType("application/json").content(cuerpo)).andExpect(status().isCreated()).andExpect(jsonPath("$.roles.length()").value(3)).andExpect(jsonPath("$.password").doesNotExist());}
 @ParameterizedTest @ValueSource(strings={"vacio","passwordCorta","sinRoles","rolNulo","rolInvalido"}) void altaInvalidaNoInvocaServicio(String caso) throws Exception {String c=switch(caso){case "vacio"->"{}";case "passwordCorta"->cuerpo.replace("clave-prueba","corta");case "sinRoles"->cuerpo.replace("[\"ADMINISTRADOR\",\"NOTIFICADOR\",\"ANALISTA\"]","[]");case "rolNulo"->cuerpo.replace("[\"ADMINISTRADOR\",\"NOTIFICADOR\",\"ANALISTA\"]","[null]");default->cuerpo.replace("ANALISTA","INVALIDO");};mvc.perform(post("/api/usuarios").contentType("application/json").content(c)).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void usuarioNoEncontradoEs404() throws Exception {when(service.getUsuarioById(99L)).thenThrow(new RecursoNoEncontradoException("No existe"));mvc.perform(get("/api/usuarios/99")).andExpect(status().isNotFound());}
 @Test void nombreDuplicadoEs409() throws Exception {when(service.saveUser(any())).thenThrow(new RecursoYaExisteException("Duplicado"));mvc.perform(post("/api/usuarios").contentType("application/json").content(cuerpo)).andExpect(status().isConflict());}
 @Test void edicionRecibeUsuarioAutenticado() throws Exception {mvc.perform(put("/api/usuarios/1").contentType("application/json").content("{\"username\":\"nuevo\",\"roles\":[\"ANALISTA\"],\"activo\":true}")).andExpect(status().isOk());verify(service).updateUser(eq(1L),any(),eq("admin-prueba"));}
 @Test void idNoNumericoEs400() throws Exception {mvc.perform(get("/api/usuarios/no-numero")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
}
