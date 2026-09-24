package com.intendencia.gestion_morosidad_api.modules.tributo.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoResponse;
import com.intendencia.gestion_morosidad_api.modules.tributo.service.TributoService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TributoController.class)
@WithMockUser
class TributoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TributoService tributoService;

    @Test
    void listarDevuelveElCatalogo() throws Exception {
        when(tributoService.listar()).thenReturn(List.of(
                new TributoResponse(1L, "101", null),
                new TributoResponse(2L, "3802", "Tributo de ejemplo")));

        mockMvc.perform(get("/api/tributos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigo").value("101"))
                .andExpect(jsonPath("$[1].descripcion").value("Tributo de ejemplo"));
    }
}
