package com.intendencia.gestion_morosidad_api.modules.gestion;

import com.intendencia.gestion_morosidad_api.modules.gestion.dto.*;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GestionController.class)
@WithMockUser(username = "operador", roles = "NOTIFICADOR")
class GestionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean GestionService service;
    String body = "{\"deudaId\":10,\"accionId\":2,\"resultado\":\"EXITOSA\",\"observacion\":\"Se contactó\"}";
    @Test void registraGestionConUsuarioDeSesionYDevuelve201() throws Exception {
        when(service.createGestion(any(), eq("operador"))).thenReturn(new GestionResponse(1L, LocalDateTime.of(2026,10,12,17,0), ResultadoGestion.EXITOSA, "Se contactó", 10L, 2L, "Llamada", 3L, "operador", "123", "456", List.of()));
        mvc.perform(post("/api/gestiones").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.deudaId").value(10)).andExpect(jsonPath("$.usuarioNombre").value("operador"));
        verify(service).createGestion(any(), eq("operador"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RESPONDIO", "NO_RESPONDIO", "PROXIMO_A_VENIR"})
    void admiteResultadosNuevos(String resultado) throws Exception {
        mvc.perform(post("/api/gestiones").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.replace("EXITOSA", resultado)))
                .andExpect(status().isCreated());
        verify(service).createGestion(argThat(r -> r.resultado().name().equals(resultado)), eq("operador"));
    }
    @Test void consultaHistorialPorDeuda() throws Exception {
        when(service.listarPorDeuda(10L)).thenReturn(List.of());
        mvc.perform(get("/api/gestiones/deuda/10")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        verify(service).listarPorDeuda(10L);
    }
    @Test void consultaInexistenteDevuelve404() throws Exception {
        when(service.listarPorDeuda(99L)).thenThrow(new RecursoNoEncontradoException("No existe la deuda"));
        mvc.perform(get("/api/gestiones/deuda/99")).andExpect(status().isNotFound());
    }
    @Test void rechazaCamposObligatoriosAusentes() throws Exception {
        mvc.perform(post("/api/gestiones").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.resultado").exists());
        verifyNoInteractions(service);
    }
    @Test void rechazaObservacionLarga() throws Exception {
        mvc.perform(post("/api/gestiones").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.replace("Se contactó", "x".repeat(256))))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void rechazaResultadoInvalido() throws Exception {
        mvc.perform(post("/api/gestiones").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.replace("EXITOSA", "INVALIDO")))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void rechazaIdentificadoresNegativos() throws Exception {
        mvc.perform(post("/api/gestiones").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.replace(":10", ":-10")))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
}
