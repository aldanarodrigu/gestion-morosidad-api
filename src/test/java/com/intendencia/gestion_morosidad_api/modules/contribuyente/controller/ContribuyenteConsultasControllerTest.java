package com.intendencia.gestion_morosidad_api.modules.contribuyente.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.intendencia.gestion_morosidad_api.modules.contacto.service.ContactoService;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContribuyenteController.class)
@WithMockUser
class ContribuyenteConsultasControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ContribuyenteService contribuyenteService;
    @MockitoBean private ContactoService contactoService;

    @Test
    void listadoSinFiltrosConservaLaRutaActual() throws Exception {
        when(contribuyenteService.listarContribuyentes(null, null))
                .thenReturn(List.of(new ContribuyenteResponse("123", "Ana Pérez", null)));

        mockMvc.perform(get("/api/contribuyentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cm").value("123"))
                .andExpect(jsonPath("$[0].nombre").value("Ana Pérez"));

        verify(contribuyenteService).listarContribuyentes(null, null);
    }

    @Test
    void listadoRecibeFiltrosOpcionales() throws Exception {
        when(contribuyenteService.listarContribuyentes("ana", "123"))
                .thenReturn(List.of(new ContribuyenteResponse("123", "Ana Pérez", "1234567-8")));

        mockMvc.perform(get("/api/contribuyentes")
                        .param("nombre", "ana")
                        .param("documento", "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documento").value("1234567-8"));

        verify(contribuyenteService).listarContribuyentes("ana", "123");
    }

    @Test
    void padronesDelContribuyenteSeBuscanPorCm() throws Exception {
        ContribuyenteResponse contribuyente = new ContribuyenteResponse("123", "Ana Pérez", "1234567-8");
        when(contribuyenteService.listarPadronesPorCm("123"))
                .thenReturn(Optional.of(List.of(
                        new PadronResponse("123", "4567", "COM", null, null, null, contribuyente),
                        new PadronResponse("124", "4568", "COM", null, null, null, contribuyente))));

        mockMvc.perform(get("/api/contribuyentes/123/padrones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numeroPadron").value("4567"))
                .andExpect(jsonPath("$[1].numeroPadron").value("4568"))
                .andExpect(jsonPath("$[0].contribuyente.nombre").value("Ana Pérez"));

        verify(contribuyenteService).listarPadronesPorCm("123");
    }

    @Test
    void padronesConCmInexistenteDevuelve404() throws Exception {
        when(contribuyenteService.listarPadronesPorCm("999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/contribuyentes/999/padrones"))
                .andExpect(status().isNotFound());
    }
}
