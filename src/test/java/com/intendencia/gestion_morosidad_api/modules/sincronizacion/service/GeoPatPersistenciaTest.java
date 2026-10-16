package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopat.client.GeoPatClient;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.tributo.repository.TributoRepository;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Datos sintéticos en PostgreSQL desechable. Nunca consulta la API ni usa la base de trabajo. */
@EnabledIfEnvironmentVariable(named = "GEOPAT_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {"sincronizacion.automatica.habilitada=false", "geopagos.username=test"})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMINISTRADOR")
class GeoPatPersistenciaTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("GEOPAT_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> "morosidad");
        r.add("spring.datasource.password", () -> "morosidad-local");
    }
    @Autowired SincronizacionDeudasProcessor processor;
    @Autowired DeudaRepository deudas;
    @Autowired PadronRepository padrones;
    @Autowired TributoRepository tributos;
    @Autowired EntityManager em;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;
    @MockitoBean GeoPatClient geoPatClient;

    GeoPagosFacturaPendienteDto fila(long cm, String tipo, String codigos, String importe, String documento) {
        return new GeoPagosFacturaPendienteDto(cm, "000123", null, null, tipo, null,
                documento == null ? null : "Persona de prueba", documento, "099000111", null, null, null,
                "NO", codigos, importe == null ? null : new BigDecimal(importe),
                LocalDate.now(clock).minusDays(100), LocalDate.now(clock).minusDays(10), "2026");
    }
    void importar(OrigenDatos origen, GeoPagosFacturaPendienteDto... filas) {
        processor.procesar(List.of(filas), List.of(), List.of(), LocalDate.now(clock), LocalDateTime.now(clock), origen);
        em.flush(); em.clear();
    }

    @Test void mismoCmYCodigoDeTributoPuedenExistirEnAmbosOrigenesSinSobrescribirse() {
        importar(OrigenDatos.INTENDENCIA, fila(123, "CIU", "1", "60", "DOC-A"));
        importar(OrigenDatos.SUCIVE, fila(123, "PAT", "1, 2", "40", null));
        var intPadron = padrones.findByCmAndOrigen("123", OrigenDatos.INTENDENCIA).orElseThrow();
        var sucPadron = padrones.findByCmAndOrigen("123", OrigenDatos.SUCIVE).orElseThrow();
        assertThat(intPadron.getId()).isNotEqualTo(sucPadron.getId());
        assertThat(intPadron.getTipoPadron()).isEqualTo("CIU");
        assertThat(sucPadron.getTipoPadron()).isEqualTo("PAT");
        assertThat(sucPadron.getContribuyente().getNombre()).isEqualTo("(sin nombre)");
        assertThat(tributos.findByCodigoAndOrigen("1", OrigenDatos.INTENDENCIA).orElseThrow().getId())
                .isNotEqualTo(tributos.findByCodigoAndOrigen("1", OrigenDatos.SUCIVE).orElseThrow().getId());
        importar(OrigenDatos.SUCIVE, fila(123, "PAT", "2", "45", null));
        assertThat(deudas.count()).isEqualTo(2);
        assertThat(deudas.findByPadronCmAndPadronOrigen("123", OrigenDatos.INTENDENCIA).orElseThrow().getImporte()).isEqualByComparingTo("60");
        assertThat(deudas.findByPadronCmAndPadronOrigen("123", OrigenDatos.SUCIVE).orElseThrow().getImporte()).isEqualByComparingTo("45");
    }

    @Test void unaSincronizacionSoloCancelaDeudasDeSuPropioOrigen() {
        importar(OrigenDatos.INTENDENCIA, fila(123, "CIU", "1", "60", null));
        importar(OrigenDatos.SUCIVE, fila(123, "PAT", "1", "40", null), fila(124, "PAT", "2", "50", null));
        importar(OrigenDatos.SUCIVE, fila(124, "PAT", "2", "50", null));
        assertThat(deudas.findByPadronCmAndPadronOrigen("123", OrigenDatos.SUCIVE).orElseThrow().getEstado()).isEqualTo(EstadoDeuda.CANCELADA);
        assertThat(deudas.findByPadronCmAndPadronOrigen("123", OrigenDatos.INTENDENCIA).orElseThrow().getEstado()).isEqualTo(EstadoDeuda.PENDIENTE);
        importar(OrigenDatos.INTENDENCIA, fila(123, "CIU", "1", "70", null));
        assertThat(deudas.findByPadronCmAndPadronOrigen("124", OrigenDatos.SUCIVE).orElseThrow().getEstado()).isEqualTo(EstadoDeuda.PENDIENTE);
    }

    @Test void filtraAmbasVistasPorOrigenYSeparaContactosDelMismoCm() throws Exception {
        importar(OrigenDatos.INTENDENCIA, fila(123, "CIU", "1", "60", "DOC-A"));
        importar(OrigenDatos.SUCIVE, fila(123, "PAT", "1", "40", null));
        mvc.perform(get("/api/deudas").param("origen", "SUCIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].padron.origen").value("SUCIVE"));
        mvc.perform(get("/api/deudas/agrupadas").param("origen", "INTENDENCIA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].importeTotal").value(60));
        mvc.perform(get("/api/contribuyentes/123/contactos").param("origen", "SUCIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].origen").value("API_GEOPAT_PENDIENTES"));
        mvc.perform(get("/api/contribuyentes/123/contactos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].origen").value("API_FACTURAS_PENDIENTES"));
        mvc.perform(get("/api/contribuyentes/123").param("origen", "SUCIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.origen").value("SUCIVE"));
    }

    @Test void soloImportaSaldoPositivoYLaProteccionDeVaciosEsPorOrigen() {
        importar(OrigenDatos.INTENDENCIA, fila(123, "CIU", "1", "60", null));
        importar(OrigenDatos.SUCIVE);
        importar(OrigenDatos.SUCIVE, fila(321, "PAT", "1", "0", null), fila(322, "PAT", "2", "-10", null), fila(323, "PAT", null, null, null));
        assertThat(padrones.findAllByOrigen(OrigenDatos.SUCIVE)).isEmpty();
        importar(OrigenDatos.SUCIVE, fila(123, "PAT", "1", "40", null));
        assertThatThrownBy(() -> importar(OrigenDatos.SUCIVE)).isInstanceOf(IntegracionExternaException.class);
        assertThat(deudas.findByPadronCmAndPadronOrigen("123", OrigenDatos.INTENDENCIA).orElseThrow().getEstado()).isEqualTo(EstadoDeuda.PENDIENTE);
    }

    @Test void agrupaPorDocumentoConservandoElFiltroDeOrigen() throws Exception {
        importar(OrigenDatos.INTENDENCIA, fila(123, "CIU", "1", "60", "DOC-COMPARTIDO"));
        importar(OrigenDatos.SUCIVE, fila(123, "PAT", "1", "40", "DOC-COMPARTIDO"));
        mvc.perform(get("/api/deudas/agrupadas"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].importeTotal").value(100));
        mvc.perform(get("/api/deudas/agrupadas").param("origen", "SUCIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].importeTotal").value(40))
                .andExpect(jsonPath("$.contenido[0].deudas.length()").value(1));
    }

    @Test void endpointAdminSincronizaGeoPatYDevuelveOrigenEnLaCartera() throws Exception {
        when(geoPatClient.obtenerFacturasPendientes()).thenReturn(new IntendenciaApiResponse<>(List.of(fila(999, "PAT", "1", "10", null)), null));
        when(geoPatClient.obtenerFacturasCanceladas(any(), any())).thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(geoPatClient.obtenerContribuyentes()).thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        mvc.perform(post("/api/sincronizaciones/deudas").param("origen", "SUCIVE").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deudasCreadas").value(1));
        em.flush(); em.clear();
        mvc.perform(get("/api/deudas").param("origen", "SUCIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].padron.cm").value("999"));
    }
}
