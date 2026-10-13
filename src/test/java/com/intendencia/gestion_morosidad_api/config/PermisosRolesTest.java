package com.intendencia.gestion_morosidad_api.config;

import com.intendencia.gestion_morosidad_api.modules.deuda.controller.DeudaController;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.DeudaService;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.controller.ContribuyenteController;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.ContactoService;
import com.intendencia.gestion_morosidad_api.modules.padron.controller.PadronController;
import com.intendencia.gestion_morosidad_api.modules.padron.service.PadronService;
import com.intendencia.gestion_morosidad_api.modules.gestion.*;
import com.intendencia.gestion_morosidad_api.modules.accion.*;
import com.intendencia.gestion_morosidad_api.modules.usuario.*;
import com.intendencia.gestion_morosidad_api.modules.tributo.controller.TributoController;
import com.intendencia.gestion_morosidad_api.modules.tributo.service.TributoService;
import com.intendencia.gestion_morosidad_api.modules.segmento.controller.ConfiguracionSegmentoController;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ConfiguracionSegmentoService;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.controller.SincronizacionController;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.service.SincronizacionDeudasService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({com.intendencia.gestion_morosidad_api.modules.deuda.controller.CarteraAgrupadaController.class, DeudaController.class, ContribuyenteController.class, PadronController.class,
        GestionController.class, AccionController.class, UsuarioController.class, TributoController.class,
        ConfiguracionSegmentoController.class, SincronizacionController.class})
@Import(SecurityConfig.class)
class PermisosRolesTest {
    @Autowired MockMvc mvc;
    @MockitoBean DeudaService deudas;
    @MockitoBean com.intendencia.gestion_morosidad_api.modules.deuda.service.CarteraAgrupadaService agrupadas;
    @MockitoBean ContribuyenteService contribuyentes;
    @MockitoBean ContactoService contactos;
    @MockitoBean PadronService padrones;
    @MockitoBean GestionService gestiones;
    @MockitoBean AccionService acciones;
    @MockitoBean UsuarioService usuarios;
    @MockitoBean TributoService tributos;
    @MockitoBean ConfiguracionSegmentoService periodos;
    @MockitoBean SincronizacionDeudasService sincronizacion;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;
    String gestion = """
            {"deudaId":1,"accionId":1,"resultado":"EXITOSA"}
            """;
    String contacto = """
            {"tipoContacto":"EMAIL","valor":"correo@example.test","origen":"Llamada","esDeContribuyente":true}
            """;

    @Test void administradorConsultaTodoYEditaContactosSinRegistrarGestiones() throws Exception {
        var contribuyente = new com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse("123", "Persona", "90000001");
        var padron = new com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse("123", "123", null, null, null, null, contribuyente);
        org.mockito.Mockito.when(padrones.buscarPorNumeroPadron("123", null)).thenReturn(java.util.List.of(padron));
        org.mockito.Mockito.when(padrones.buscarContribuyentes("123", null)).thenReturn(java.util.List.of(contribuyente));
        org.mockito.Mockito.when(contribuyentes.buscarPorCm("123")).thenReturn(java.util.Optional.of(contribuyente));
        org.mockito.Mockito.when(contribuyentes.listarPadronesPorCm("123")).thenReturn(java.util.Optional.of(java.util.List.of(padron)));
        for (String ruta : new String[]{"/api/deudas", "/api/deudas/agrupadas", "/api/deudas/1", "/api/deudas/1/tributos",
                "/api/contribuyentes", "/api/contribuyentes/123", "/api/contribuyentes/123/padrones",
                "/api/contribuyentes/123/contactos", "/api/contribuyentes/id/1/deudas", "/api/contribuyentes/123/deudas",
                "/api/padrones/123", "/api/padrones/123/contribuyente", "/api/padrones/123/deudas",
                "/api/gestiones", "/api/gestiones/1", "/api/gestiones/deuda/1", "/api/gestiones/deuda/1/acciones-disponibles"}) {
            mvc.perform(get(ruta).with(user("admin").roles("ADMINISTRADOR"))).andExpect(status().isOk());
        }
        mvc.perform(put("/api/contribuyentes/123/contactos").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(contacto)).andExpect(status().isOk());
        mvc.perform(put("/api/contribuyentes/123/contactos").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"id":7,"tipoContacto":"EMAIL","valor":"nuevo@example.test","origen":"Atención presencial","esDeContribuyente":true}
                                """))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(contactos, org.mockito.Mockito.times(2)).guardarOActualizar(
                org.mockito.ArgumentMatchers.eq("123"), org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/gestiones").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(gestion)).andExpect(status().isForbidden());
        org.mockito.Mockito.verify(gestiones, org.mockito.Mockito.never()).createGestion(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
    @ParameterizedTest @ValueSource(strings = {"NOTIFICADOR", "ANALISTA"})
    void rolesOperativosConsultanYEditanContactos(String rol) throws Exception {
        for (String ruta : new String[]{"/api/deudas", "/api/contribuyentes", "/api/contribuyentes/123/contactos", "/api/gestiones/deuda/1"})
            mvc.perform(get(ruta).with(user("operador").roles(rol))).andExpect(status().isOk());
        mvc.perform(put("/api/contribuyentes/123/contactos").with(user("operador").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content(contacto)).andExpect(status().isOk());
    }
    @Test void soloNotificadorRegistraGestiones() throws Exception {
        mvc.perform(post("/api/gestiones").with(user("notificador").roles("NOTIFICADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(gestion)).andExpect(status().isCreated());
        mvc.perform(post("/api/gestiones").with(user("analista").roles("ANALISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content(gestion)).andExpect(status().isForbidden());
    }
    @Test void administradorMantieneFuncionesAdministrativas() throws Exception {
        for (String ruta : new String[]{"/api/usuarios", "/api/acciones", "/api/tributos", "/api/configuraciones-segmento"})
            mvc.perform(get(ruta).with(user("admin").roles("ADMINISTRADOR"))).andExpect(status().isOk());
        mvc.perform(post("/api/acciones").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Nueva accion\",\"segmentoId\":1}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/deudas/1/estado").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_GESTION\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/sincronizaciones/deudas").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings = {"NOTIFICADOR", "ANALISTA"})
    void rolesSinAdminNoAdministran(String rol) throws Exception {
        mvc.perform(get("/api/usuarios").with(user("operador").roles(rol))).andExpect(status().isForbidden());
        mvc.perform(post("/api/acciones").with(user("operador").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Nueva accion\",\"segmentoId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/sincronizaciones/deudas").with(user("operador").roles(rol))).andExpect(status().isForbidden());
        mvc.perform(put("/api/deudas/1/estado").with(user("operador").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_GESTION\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void administradorConNotificadorSumaAmbosPermisos() throws Exception {
        mvc.perform(get("/api/deudas").with(user("mixto").roles("ADMINISTRADOR", "NOTIFICADOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").with(user("mixto").roles("ADMINISTRADOR", "NOTIFICADOR"))).andExpect(status().isOk());
        mvc.perform(post("/api/gestiones").with(user("mixto").roles("ADMINISTRADOR", "NOTIFICADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(gestion)).andExpect(status().isCreated());
    }
    @Test void administradorConAnalistaConsultaPeroNoRegistraGestiones() throws Exception {
        mvc.perform(get("/api/deudas").with(user("mixto").roles("ADMINISTRADOR", "ANALISTA"))).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").with(user("mixto").roles("ADMINISTRADOR", "ANALISTA"))).andExpect(status().isOk());
        mvc.perform(post("/api/gestiones").with(user("mixto").roles("ADMINISTRADOR", "ANALISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content(gestion)).andExpect(status().isForbidden());
    }
}
