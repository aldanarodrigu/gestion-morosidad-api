package com.intendencia.gestion_morosidad_api.modules.contribuyente.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.intendencia.gestion_morosidad_api.modules.contacto.service.ContactoService;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
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
    void listadoSinFiltrosDevuelveUnaPaginaOrdenadaPorNombre() throws Exception {
        when(contribuyenteService.listarContribuyentes(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(new ContribuyenteResponse("123", "Ana Pérez", null)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/contribuyentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].cm").value("123"))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.totalElementos").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(contribuyenteService).listarContribuyentes(isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("contribuyente.nombre")).isNotNull();
    }

    @Test
    void listadoRecibeFiltrosOpcionalesYPagina() throws Exception {
        when(contribuyenteService.listarContribuyentes(eq("ana"), eq("123"), any(Pageable.class)))
                .thenReturn(new PaginaResponse<>(List.of(new ContribuyenteResponse("123", "Ana Pérez", "1234567-8")), 2, 10, 21, 3));

        mockMvc.perform(get("/api/contribuyentes")
                        .param("nombre", "ana")
                        .param("documento", "123")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].documento").value("1234567-8"))
                .andExpect(jsonPath("$.pagina").value(2));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(contribuyenteService).listarContribuyentes(eq("ana"), eq("123"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
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
