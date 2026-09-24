package com.intendencia.gestion_morosidad_api.deuda.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.intendencia.gestion_morosidad_api.deuda.dto.DeudaFiltro;
import com.intendencia.gestion_morosidad_api.deuda.dto.DeudaResponse;
import com.intendencia.gestion_morosidad_api.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.deuda.service.DeudaService;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeudaController.class)
@WithMockUser
class DeudaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeudaService deudaService;

    @Test
    void listarPasaLosFiltrosAlService() throws Exception {
        when(deudaService.listar(any(), any()))
                .thenReturn(new PaginaResponse<>(List.of(deuda(1L)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/deudas")
                        .param("estado", "PENDIENTE")
                        .param("padron", "4567")
                        .param("contribuyente", "perez")
                        .param("localidad", "Libertad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].padron.cm").value("CM-1"))
                .andExpect(jsonPath("$.contenido[0].contribuyente.nombre").value("Persona de ejemplo"))
                .andExpect(jsonPath("$.totalElementos").value(1));

        verify(deudaService).listar(eq(new DeudaFiltro(EstadoDeuda.PENDIENTE, "4567", "perez", "Libertad")), any());
    }

    @Test
    void listarConEstadoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/deudas").param("estado", "NO_EXISTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Valor inválido para el parámetro 'estado'"));
    }

    @Test
    void obtenerInexistenteDevuelve404() throws Exception {
        when(deudaService.obtener(99L)).thenThrow(new RecursoNoEncontradoException("No existe la deuda con id 99"));

        mockMvc.perform(get("/api/deudas/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listarPorPadronDevuelveLista() throws Exception {
        when(deudaService.listarPorPadron("4567")).thenReturn(List.of(deuda(1L), deuda(2L)));

        mockMvc.perform(get("/api/padrones/4567/deudas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void actualizarEstadoDevuelveLaDeudaActualizada() throws Exception {
        when(deudaService.actualizarEstado(1L, EstadoDeuda.EN_GESTION)).thenReturn(deuda(1L));

        mockMvc.perform(put("/api/deudas/1/estado").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_GESTION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void actualizarEstadoSinEstadoDevuelve400ConDetalleDelCampo() throws Exception {
        mockMvc.perform(put("/api/deudas/1/estado").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.estado").value("El estado es obligatorio"));
    }

    @Test
    void actualizarEstadoConValorInexistenteDevuelve400() throws Exception {
        mockMvc.perform(put("/api/deudas/1/estado").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"PAGADA\"}"))
                .andExpect(status().isBadRequest());
    }

    private static DeudaResponse deuda(Long id) {
        return DeudaResponse.builder()
                .id(id)
                .padron(new PadronResponse("CM-" + id, "4567", "CIU", "Libertad", null, null))
                .contribuyente(new ContribuyenteResponse("CM-" + id, "Persona de ejemplo", "1234567-8"))
                .estado(EstadoDeuda.PENDIENTE)
                .build();
    }
}
