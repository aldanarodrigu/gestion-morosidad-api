package com.intendencia.gestion_morosidad_api.modules.deuda.controller;

import com.intendencia.gestion_morosidad_api.config.SecurityConfig;
import com.intendencia.gestion_morosidad_api.modules.deuda.dto.DeudaFiltro;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.CarteraExportacionService;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CarteraExportacionController.class)
@Import(SecurityConfig.class)
class CarteraExportacionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CarteraExportacionService servicio;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "NOTIFICADOR", "ANALISTA"})
    void todosLosRolesExportanSinLimiteDePagina(String rol) throws Exception {
        var filtro = new DeudaFiltro(null, null, "001", null, 3L, null, true, "CIU", OrigenDatos.SUCIVE);
        byte[] archivo = {80, 75, 3, 4};
        when(servicio.exportar(filtro, Sort.by(Sort.Direction.DESC, "importe"), true)).thenReturn(archivo);
        mvc.perform(get("/api/deudas/exportar").with(user("usuario").roles(rol))
                        .param("contribuyente", "001").param("segmento", "3").param("tipoPadron", "CIU")
                        .param("origen", "SUCIVE").param("conContacto", "true").param("agrupar", "true")
                        .param("sort", "importe,desc").param("page", "10").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=cartera-morosidad.xlsx"))
                .andExpect(content().bytes(archivo));
        verify(servicio).exportar(filtro, Sort.by(Sort.Direction.DESC, "importe"), true);
    }

    @Test
    void sinFiltrosExportaCarteraCompleta() throws Exception {
        var filtro = new DeudaFiltro(null, null, null, null, null, null, null, null, null);
        when(servicio.exportar(filtro, Sort.by("deudaDesde"), false)).thenReturn(new byte[]{1});
        mvc.perform(get("/api/deudas/exportar").with(user("usuario").roles("ANALISTA")))
                .andExpect(status().isOk());
        verify(servicio).exportar(filtro, Sort.by("deudaDesde"), false);
    }

    @Test
    void exigeAutenticacion() throws Exception {
        mvc.perform(get("/api/deudas/exportar")).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }
}
