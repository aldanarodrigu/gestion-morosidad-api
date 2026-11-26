package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaCanceladaDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.modules.cancelacion.Cancelacion;
import com.intendencia.gestion_morosidad_api.modules.cancelacion.CancelacionRepository;
import com.intendencia.gestion_morosidad_api.modules.cancelacion.FiltroCancelaciones;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion;
import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaPaginador;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sincronización completa contra PostgreSQL desechable con respuestas de la API con la forma real
 * de GeoPagos (src/test/resources/sincronizacion/casos-reales): claves en mayúsculas, CM y padrón
 * como número o texto, importes como texto, fechas "0000-00-00", filas duplicadas o incompletas,
 * el mismo número de padrón en dos localidades y una persona con varios padrones.
 * Los datos personales son ficticios. Nunca consulta la API ni usa la base de trabajo.
 *
 * <pre>
 * docker exec morosidad-local createdb -U morosidad sincronizacion_test
 * SINCRONIZACION_TEST_DB_URL=jdbc:postgresql://localhost:5434/sincronizacion_test \
 *   ./mvnw test -Dtest=SincronizacionCasosRealesPersistenciaTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "SINCRONIZACION_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {"sincronizacion.automatica.habilitada=false", "geopagos.username=test"})
@Transactional
@AutoConfigureMockMvc
class SincronizacionCasosRealesPersistenciaTest {

    static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");
    /** 24/09/2026 12:00 en Uruguay. */
    static final Instant MEDIODIA = Instant.parse("2026-09-24T15:00:00Z");
    static final LocalDate DIA_1 = LocalDate.of(2026, 9, 24);
    static final LocalDate DIA_2 = DIA_1.plusDays(1);

    static final String MARIA = "1.234.567-8";
    static final String JUAN = "4.567.890-1";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        String url = System.getenv("SINCRONIZACION_TEST_DB_URL");
        // Reproduce la configuración de lotes usada en la aplicación.
        r.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "reWriteBatchedInserts=true");
        r.add("spring.datasource.username", () -> "morosidad");
        r.add("spring.datasource.password", () -> "morosidad-local");
    }

    @TestBean Clock clock;

    static Clock clock() {
        return Clock.fixed(MEDIODIA, URUGUAY);
    }

    @Autowired SincronizacionDeudasProcessor processor;
    @Autowired SincronizacionDeudasService service;
    @Autowired DeudaRepository deudas;
    @Autowired JdbcTemplate jdbc;
    @Autowired CancelacionRepository cancelaciones;
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @MockitoBean GeoPagosClient geoPagosClient;

    final JsonMapper json = JsonMapper.builder().build();

    // ================================================================ Día 1: alta inicial

    @Test
    void primeraSincronizacionImportaSoloLosRegistrosValidos() {
        ResultadoSincronizacion r = dia1();

        assertThat(r.pendientesRecibidas()).isEqualTo(7);
        assertThat(r.deudasCreadas()).isEqualTo(3);
        assertThat(r.deudasActualizadas()).isZero();
        assertThat(r.deudasCanceladas()).isZero();
        // Saldo cero, padrón vacío, importe nulo y CM repetido.
        assertThat(r.registrosOmitidos()).isEqualTo(4);
        assertThat(r.advertencias()).anyMatch(a -> a.contains("100201 repetido"))
                .anyMatch(a -> a.contains("100204") && a.contains("sin deuda vencida positiva"))
                .anyMatch(a -> a.contains("100205") && a.contains("padr"))
                .anyMatch(a -> a.contains("100206") && a.contains("importe"));
        // Las advertencias no exponen datos personales.
        assertThat(String.join(" ", r.advertencias())).doesNotContain("GONZALEZ", MARIA, "SALDO A FAVOR");

        assertThat(cms()).containsExactlyInAnyOrder("100201", "100202", "100203");
        assertThat(count("SELECT count(*) FROM padrones WHERE cm IN ('100204','100205','100206')")).isZero();
        assertThat(count("SELECT count(*) FROM contribuyentes WHERE documento = '2.222.222-2'")).isZero();
    }

    @Test
    void guardaLosDatosDeLaFilaTalComoLlegan() {
        dia1();

        Deuda maria = deuda("100201");
        // La primera aparición del CM gana; la fila duplicada no pisa el importe.
        assertThat(maria.getImporte()).isEqualByComparingTo("15432.50");
        assertThat(maria.getDeudaFacturas()).isEqualByComparingTo("15432.50");
        assertThat(maria.getDeudaDesde()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(maria.getUltimoVencimiento()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(maria.getCantidadFacturasVencidas()).isEqualTo(6);
        assertThat(maria.getAnioDesde()).isEqualTo(2026);
        assertThat(maria.getTributos()).extracting(Tributo::getCodigo).containsExactlyInAnyOrder("101", "3802");
        assertThat(maria.getFechaSincronizacion()).isEqualTo(LocalDateTime.of(2026, 9, 24, 12, 0));
        assertThat(maria.getPadron().getOrigen()).isEqualTo(OrigenDatos.INTENDENCIA);

        Deuda juan = deuda("100202");
        assertThat(juan.getPadron().getBlock()).isEqualTo("B");
        assertThat(juan.getPadron().getUnidad()).isEqualTo("003");
        assertThat(juan.getPadron().getTipoPadron()).isEqualTo("COM");
        assertThat(juan.getImporte()).isEqualByComparingTo("8000");

        // Los tributos se crean una vez aunque los usen varias deudas.
        assertThat(count("SELECT count(*) FROM tributos WHERE codigo = '101' AND origen = 'INTENDENCIA'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM tributos WHERE codigo = '3802' AND origen = 'INTENDENCIA'")).isEqualTo(1);
    }

    @Test
    void mismoNumeroDePadronEnDosLocalidadesSonPadronesDistintos() {
        dia1();

        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT cm, localidad FROM padrones WHERE numero_padron = '4567' ORDER BY cm");
        assertThat(filas).extracting(f -> f.get("cm"), f -> f.get("localidad"))
                .containsExactly(org.assertj.core.api.Assertions.tuple("100201", "MALDONADO"),
                        org.assertj.core.api.Assertions.tuple("100202", "SAN CARLOS"));
        assertThat(deuda("100201").getPadron().getContribuyente().getId())
                .isNotEqualTo(deuda("100202").getPadron().getContribuyente().getId());
    }

    @Test
    void unaPersonaConVariosPadronesEsUnSoloContribuyente() {
        dia1();

        assertThat(count("SELECT count(*) FROM contribuyentes WHERE documento = ?", MARIA)).isEqualTo(1);
        assertThat(deuda("100201").getPadron().getContribuyente().getId())
                .isEqualTo(deuda("100203").getPadron().getContribuyente().getId());
    }

    @Test
    void estadoYSegmentoInicialesSegunConvenioYAntiguedad() {
        dia1();

        Deuda maria = deuda("100201");   // 198 días
        Deuda juan = deuda("100202");    // 54 días, con convenio
        Deuda antigua = deuda("100203"); // desde 2019
        assertThat(maria.getEstado()).isEqualTo(EstadoDeuda.PENDIENTE);
        assertThat(maria.getSegmentoMora().getNombre()).isEqualTo("Mora Inicial");
        assertThat(juan.getEstado()).isEqualTo(EstadoDeuda.EN_CONVENIO);
        assertThat(juan.isConvenio()).isTrue();
        assertThat(juan.getSegmentoMora().getNombre()).isEqualTo("Mora Temprana");
        assertThat(antigua.getSegmentoMora().getNombre()).isEqualTo("Mora Tardía");
    }

    @Test
    void contactosSoloDePadronesConDeudaYSinValoresVacios() {
        dia1();

        Long juan = contribuyenteId(JUAN);
        assertThat(contactos(juan)).containsExactlyInAnyOrder(
                "TELEFONO|42221234|" + SincronizacionContactosService.PENDIENTES + "|true",
                "EMAIL|juan.perez@correo.test|" + SincronizacionContactosService.PENDIENTES + "|true",
                "DOMICILIO|RUTA 9|" + SincronizacionContactosService.PENDIENTES + "|true");
        assertThat(count("SELECT count(*) FROM contactos WHERE valor IN ('091000000', '091111111')")).isZero();
        assertThat(count("SELECT count(*) FROM contactos WHERE trim(valor) = ''")).isZero();
    }

    @Test
    void contactosDePersonasYDelUltimoPagadorSeDistinguen() {
        dia1();

        assertThat(contactos(contribuyenteId(MARIA))).contains(
                "TELEFONO|42229999|" + SincronizacionContactosService.PERSONAS + "|true",
                "EMAIL|maria.gonzalez@correo.test|" + SincronizacionContactosService.PERSONAS + "|true",
                // Quien hizo el último GeoPago puede no ser el titular.
                "TELEFONO|098765432|" + SincronizacionContactosService.GEOPAGOS + "|false",
                "EMAIL|pedro@correo.test|" + SincronizacionContactosService.GEOPAGOS + "|false");
    }

    /**
     * María tiene dos padrones: 100201 trae teléfono y domicilio y 100203 los trae vacíos.
     * "Los valores vacíos de la API se ignoran": el padrón sin datos no debe borrar los del otro.
     */
    @Test
    void padronSinContactoNoDescartaElContactoDeOtroPadronDeLaMismaPersona() {
        dia1();

        assertThat(contactos(contribuyenteId(MARIA))).contains(
                "TELEFONO|099 123 456|" + SincronizacionContactosService.PENDIENTES + "|true",
                "DOMICILIO|25 DE MAYO 1234|" + SincronizacionContactosService.PENDIENTES + "|true");
    }

    // ================================================================ Día 2: cambios

    @Test
    void segundaSincronizacionConciliaCambiosEstadosYCobros() {
        dia1();
        // Un notificador tomó la deuda de María.
        jdbc.update("UPDATE deuda SET estado = 'EN_GESTION' WHERE padron_id = (SELECT id FROM padrones WHERE cm = '100201')");
        Long idMaria = deuda("100201").getId();

        ResultadoSincronizacion r = dia2();

        assertThat(r.pendientesRecibidas()).isEqualTo(3);
        assertThat(r.cobrosRecibidos()).isEqualTo(4);
        assertThat(r.deudasCreadas()).isEqualTo(1);
        assertThat(r.deudasActualizadas()).isEqualTo(2);
        assertThat(r.deudasCanceladas()).isEqualTo(1);
        assertThat(r.registrosOmitidos()).isZero();
        // contribuyentes-geopagos no respondió: se guardan las deudas igual y se avisa.
        assertThat(r.advertencias()).singleElement().asString().contains("contribuyentes");

        Deuda maria = deuda("100201");
        assertThat(maria.getId()).isEqualTo(idMaria);
        assertThat(maria.getEstado()).isEqualTo(EstadoDeuda.EN_GESTION);
        assertThat(maria.getImporte()).isEqualByComparingTo("16010.75");
        assertThat(maria.getTributos()).extracting(Tributo::getCodigo).containsExactlyInAnyOrder("101", "3802");

        Deuda juan = deuda("100202");
        assertThat(juan.getEstado()).isEqualTo(EstadoDeuda.PENDIENTE);
        assertThat(juan.isConvenio()).isFalse();

        Deuda pagada = deuda("100203");
        assertThat(pagada.getEstado()).isEqualTo(EstadoDeuda.CANCELADA);
        assertThat(pagada.getSegmentoMora()).isNull();
        assertThat(pagada.getImporte()).isEqualByComparingTo("120000.00");
        assertThat(pagada.getFechaUltimoCobro()).isEqualTo(DIA_1);
        assertThat(pagada.getImporteUltimoCobro()).isEqualByComparingTo("120000.00");

        assertThat(count("SELECT count(*) FROM deuda")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM padrones WHERE cm = '555555'")).isZero();
    }

    @Test
    void cobroDeOtraPersonaRegistraElMovimientoSinCambiarAlTitular() {
        dia1();
        dia2();

        Deuda maria = deuda("100201");
        assertThat(maria.getFechaUltimoCobro()).isEqualTo(DIA_1);
        assertThat(maria.getImporteUltimoCobro()).isEqualByComparingTo("500.00");
        assertThat(maria.getMotivoUltimoCobro()).isEqualTo("ANULADO POR ERROR");
        assertThat(maria.getPadron().getContribuyente().getDocumento()).isEqualTo(MARIA);
        assertThat(maria.getPadron().getContribuyente().getNombre()).isEqualTo("GONZALEZ RODRIGUEZ, MARIA");
        assertThat(count("SELECT count(*) FROM contribuyentes WHERE documento = '9.999.999-9'")).isZero();
    }

    @Test
    void padronNuevoConDocumentoConocidoSeVinculaALaMismaPersona() {
        dia1();
        Long juan = contribuyenteId(JUAN);

        dia2();

        assertThat(deuda("100208").getPadron().getContribuyente().getId()).isEqualTo(juan);
        assertThat(count("SELECT count(*) FROM contribuyentes WHERE trim(documento) = ?", JUAN)).isEqualTo(1);
        assertThat(deuda("100208").getSegmentoMora().getNombre()).isEqualTo("Mora Tardía"); // 389 días
    }

    @Test
    void contactosExistentesYCorregidosNoSeSobrescribenNiDuplican() {
        dia1();
        Long juan = contribuyenteId(JUAN);
        jdbc.update("UPDATE contactos SET valor = '42220000' WHERE contribuyente_id = ? AND tipo_contacto = 'TELEFONO'", juan);
        List<String> antes = contactos(juan);

        dia2();

        assertThat(contactos(juan)).containsExactlyInAnyOrderElementsOf(antes)
                .contains("TELEFONO|42220000|" + SincronizacionContactosService.PENDIENTES + "|true");
        // María no tenía correo en esa fuente (el día 1 llegó vacío): solo se agrega ese.
        assertThat(contactos(contribuyenteId(MARIA)))
                .contains("EMAIL|otra@correo.test|" + SincronizacionContactosService.PENDIENTES + "|true");
        assertThat(count("SELECT count(*) FROM (SELECT 1 FROM contactos GROUP BY contribuyente_id, tipo_contacto, origen "
                + "HAVING count(*) > 1) repetidos")).isZero();
    }

    @Test
    void repetirLaMismaRespuestaNoCreaNiCancelaNada() {
        dia1();
        long contactosAntes = count("SELECT count(*) FROM contactos");

        ResultadoSincronizacion r = dia1();

        assertThat(r.deudasCreadas()).isZero();
        assertThat(r.deudasActualizadas()).isEqualTo(3);
        assertThat(r.deudasCanceladas()).isZero();
        assertThat(count("SELECT count(*) FROM deuda")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM contribuyentes")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM contactos")).isEqualTo(contactosAntes);
    }

    @Test
    void deudaPagadaQueVuelveAAparecerSeReactivaConservandoSuHistorial() {
        dia1();
        dia2();
        Long id = deuda("100203").getId();

        procesar(pendientes("pendientes-dia1.json"), List.of(), null, DIA_2.plusDays(1));

        Deuda deuda = deuda("100203");
        assertThat(deuda.getId()).isEqualTo(id);
        assertThat(deuda.getEstado()).isEqualTo(EstadoDeuda.PENDIENTE);
        assertThat(deuda.getFechaUltimoCobro()).isEqualTo(DIA_1);
    }

    @Test
    void respuestaVaciaConDeudasActivasNoAplicaCambios() {
        dia1();

        assertThatThrownBy(() -> procesar(List.of(), List.of(), List.of(), DIA_2))
                .isInstanceOf(IntegracionExternaException.class);

        em.clear();
        assertThat(jdbc.queryForList("SELECT estado FROM deuda", String.class))
                .containsOnly("PENDIENTE", "EN_CONVENIO");
    }

    // ================================================================ Flujo de producción

    /** El servicio (endpoint manual y cron) consulta solo el período configurado (2013 por defecto). */
    @Test
    void servicioSincronizaElPeriodoConfigurado() {
        respuestaDeLaApi("periodo-pendientes.json");

        ResultadoSincronizacion r = service.sincronizar(OrigenDatos.INTENDENCIA);
        em.flush();
        em.clear();

        assertThat(r.deudasCreadas()).isEqualTo(1);
        Deuda deuda = deuda("100201");
        assertThat(deuda.isEnPeriodoGestion()).isTrue();
        assertThat(deuda.getAnioPeriodo()).isEqualTo(2013);
        assertThat(deuda.getSegmentoMora()).isNotNull();
    }

    /**
     * README: "Si GeoPagos devuelve 0 deudas habiendo deudas activas, no se aplica ningún cambio".
     * El servicio siempre pasa el año del período: la protección también debe aplicarse con período.
     */
    @Test
    void servicioConRespuestaVaciaNoSacaLasDeudasDelPeriodo() {
        respuestaDeLaApi("periodo-pendientes.json");
        service.sincronizar(OrigenDatos.INTENDENCIA);
        em.flush();
        em.clear();

        when(geoPagosClient.obtenerFacturasPendientes(any(), any())).thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        assertThatThrownBy(() -> service.sincronizar(OrigenDatos.INTENDENCIA))
                .isInstanceOf(IntegracionExternaException.class).hasMessageContaining("no se aplicaron cambios");
        em.flush();
        em.clear();

        Deuda deuda = deuda("100201");
        assertThat(deuda.isEnPeriodoGestion()).isTrue();
        assertThat(deuda.getSegmentoMora()).isNotNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {2013, 2026})
    void descargaTruncadaNoCambiaDeudasNiElSaldoDeCartera(Integer anioMinimo) throws Exception {
        dia1();
        var builder = RestClient.builder().baseUrl("https://test.invalid");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new GeoPagosClient(new IntendenciaPaginador(builder.build(), 1000));
        servidor.expect(queryParam("include_total", "0"))
                .andRespond(withSuccess(paginaFinal("100201"), MediaType.APPLICATION_JSON));
        servidor.expect(queryParam("cm", "100202"))
                .andExpect(queryParam("include_total", "0"))
                .andRespond(withSuccess(paginaFinal("100202"), MediaType.APPLICATION_JSON));
        when(geoPagosClient.obtenerFacturasPendientes(any(), any()))
                .thenAnswer(inv -> cliente.obtenerFacturasPendientes(inv.getArgument(0), inv.getArgument(1)));
        when(geoPagosClient.obtenerFacturasPendientesPorCm(any(), any(), any()))
                .thenAnswer(inv -> cliente.obtenerFacturasPendientesPorCm(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        assertThatThrownBy(() -> service.sincronizar(OrigenDatos.INTENDENCIA, anioMinimo))
                .isInstanceOf(IntegracionExternaException.class)
                .hasMessageContaining("CM 100202").hasMessageContaining("No se aplicaron cambios");

        assertThat(deuda("100201").getImporte()).isEqualByComparingTo("15432.50");
        assertThat(deuda("100202").getEstado()).isEqualTo(EstadoDeuda.EN_CONVENIO);
        assertThat(deuda("100202").isEnPeriodoGestion()).isTrue();
        assertThat(deuda("100203").isEnPeriodoGestion()).isTrue();
        mvc.perform(get("/api/reportes/cartera").with(user("analista").roles("ANALISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.deudas").value(3))
                .andExpect(jsonPath("$.totales.importe").value(143432.50));
        servidor.verify();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = 2013)
    void unaAusenciaConfirmadaPorCmPermiteConciliar(Integer anioMinimo) {
        dia1();
        respuestaDeLaApi("pendientes-dia2.json");
        when(geoPagosClient.obtenerFacturasPendientesPorCm("100203",
                anioMinimo == null ? null : LocalDate.of(anioMinimo, 1, 1), DIA_1))
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));

        service.sincronizar(OrigenDatos.INTENDENCIA, anioMinimo);
        em.flush();
        em.clear();
        var ausente = deuda("100203");
        assertThat(deuda("100201").getImporte()).isEqualByComparingTo("16010.75");
        if (anioMinimo == null) {
            assertThat(ausente.getEstado()).isEqualTo(EstadoDeuda.CANCELADA);
        } else {
            assertThat(ausente.getEstado()).isEqualTo(EstadoDeuda.PENDIENTE);
            assertThat(ausente.isEnPeriodoGestion()).isFalse();
        }
    }

    @Test
    void alAcotarElPeriodoNoConsultaOtraVezDeudasConUltimoVencimientoAnterior() {
        dia1();
        jdbc.update("""
                UPDATE deuda SET ultimo_vencimiento = DATE '2025-12-31'
                WHERE padron_id IN (SELECT id FROM padrones WHERE cm = '100203')
                """);
        em.clear();
        respuestaDeLaApi("periodo-pendientes.json");
        when(geoPagosClient.obtenerFacturasPendientes(any(), any()))
                .thenReturn(new IntendenciaApiResponse<>(pendientes("pendientes-dia1.json").subList(0, 2), null));

        service.sincronizar(OrigenDatos.INTENDENCIA, 2026);
        em.flush();
        em.clear();
        assertThat(deuda("100203").isEnPeriodoGestion()).isFalse();
        org.mockito.Mockito.verify(geoPagosClient, org.mockito.Mockito.never())
                .obtenerFacturasPendientesPorCm(any(), any(), any());
    }

    private String paginaFinal(String cm) {
        return """
                {"data":[{"CM":%s,"NUMERO_PADRON":"4567","IMPORTE_DEUDA":1000,"DEUDA_DESDE":"2026-03-10"}],
                 "meta":{"current_page":1,"per_page":1000,"from":1,"to":1,
                         "has_more":false,"total":null,"last_page":null}}
                """.formatted(cm);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unaFilaParcialConservaLosDatosInformadosEnLaSincronizacionAnterior(boolean camposVacios) {
        sincronizarDeudaCompleta();
        var opcionales = camposVacios ? """
                , "PADTIPO":" ", "LOCALIDAD":null, "BLOCK":" ", "UNIDAD":null, "TRIBUTOS_DEUDA":", ,",
                  "CONVENIO":" ", "ULTIMO_VENCIMIENTO":"0000-00-00", "ANIOS_DEUDA":"",
                  "DEUDA_FACTURAS":null, "SALDO_A_FAVOR":null, "CANT_FACT_VENC":null
                """ : "";
        sincronizarFila("""
                {"CM":100202,"NUMERO_PADRON":4567,"IMPORTE_DEUDA":7800,"DEUDA_DESDE":"2026-08-01"%s}
                """.formatted(opcionales), 2013);

        var deuda = deuda("100202");
        assertThat(deuda.getImporte()).isEqualByComparingTo("7800");
        assertThat(deuda.getPadron().getTipoPadron()).isEqualTo("COM");
        assertThat(deuda.getPadron().getLocalidad()).isEqualTo("SAN CARLOS");
        assertThat(deuda.getPadron().getBlock()).isEqualTo("B");
        assertThat(deuda.getPadron().getUnidad()).isEqualTo("003");
        assertThat(deuda.getTributos()).extracting(Tributo::getCodigo).containsExactlyInAnyOrder("101", "3802");
        assertThat(deuda.isConvenio()).isTrue();
        assertThat(deuda.getEstado()).isEqualTo(EstadoDeuda.EN_CONVENIO);
        assertThat(deuda.getUltimoVencimiento()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(deuda.getAniosDeuda()).isEqualTo("2026");
        assertThat(deuda.getDeudaFacturas()).isEqualByComparingTo("9000");
        assertThat(deuda.getSaldoAFavor()).isEqualByComparingTo("1000");
        assertThat(deuda.getCreditoDescontado()).isEqualByComparingTo("1000");
        assertThat(deuda.getCantidadFacturasVencidas()).isEqualTo(3);
        assertThat(deuda.getCantidadFacturasConDeuda()).isEqualTo(2);
        assertThat(deuda.getAnioDesde()).isEqualTo(2026);
        assertThat(deuda.getAnioHasta()).isEqualTo(2026);
        assertThat(deuda.getCantidadAniosDeuda()).isEqualTo(1);
        assertThat(deuda.getSegmentoMora()).isNotNull();
    }

    @Test
    void tomaLosDatosNuevosYElNoExplicitoDeConvenio() {
        sincronizarDeudaCompleta();
        sincronizarFila("""
                {"CM":100202,"NUMERO_PADRON":"7777","IMPORTE_DEUDA":8100,
                 "DEUDA_DESDE":"2026-08-05","ULTIMO_VENCIMIENTO":"2026-09-05",
                 "PADTIPO":"CIU","LOCALIDAD":"MALDONADO","BLOCK":"D","UNIDAD":"004",
                 "TRIBUTOS_DEUDA":"900","CONVENIO":"NO","SALDO_A_FAVOR":0,"CREDITO_DESCONTADO":0,
                 "DEUDA_FACTURAS":8100,"CANT_FACT_VENC":2}
                """, 2013);

        var deuda = deuda("100202");
        assertThat(deuda.getImporte()).isEqualByComparingTo("8100");
        assertThat(deuda.getPadron().getNumeroPadron()).isEqualTo("7777");
        assertThat(deuda.getPadron().getTipoPadron()).isEqualTo("CIU");
        assertThat(deuda.getPadron().getLocalidad()).isEqualTo("MALDONADO");
        assertThat(deuda.getPadron().getBlock()).isEqualTo("D");
        assertThat(deuda.getPadron().getUnidad()).isEqualTo("004");
        assertThat(deuda.getTributos()).extracting(Tributo::getCodigo).containsExactly("900");
        assertThat(deuda.isConvenio()).isFalse();
        assertThat(deuda.getEstado()).isEqualTo(EstadoDeuda.PENDIENTE);
        assertThat(deuda.getDeudaDesde()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(deuda.getUltimoVencimiento()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(deuda.getDeudaFacturas()).isEqualByComparingTo("8100");
        assertThat(deuda.getSaldoAFavor()).isEqualByComparingTo("0");
        assertThat(deuda.getCreditoDescontado()).isEqualByComparingTo("0");
        assertThat(deuda.getCantidadFacturasVencidas()).isEqualTo(2);
    }

    @Test
    void unSaldoCeroCancelaYUnSaldoPositivoReactivaAunqueFaltenCamposOpcionales() {
        sincronizarDeudaCompleta();
        sincronizarFila("""
                {"CM":100202,"NUMERO_PADRON":4567,"IMPORTE_DEUDA":0,"CANT_FACT_CON_DEUDA":0}
                """, null);

        var cancelada = deuda("100202");
        assertThat(cancelada.getEstado()).isEqualTo(EstadoDeuda.CANCELADA);
        assertThat(cancelada.getImporte()).isEqualByComparingTo("0");
        assertThat(cancelada.getDeudaFacturas()).isEqualByComparingTo("9000");
        assertThat(cancelada.getCantidadFacturasConDeuda()).isZero();

        sincronizarFila("""
                {"CM":100202,"NUMERO_PADRON":4567,"IMPORTE_DEUDA":1000,"CONVENIO":"DESCONOCIDO"}
                """, null);

        var reactivada = deuda("100202");
        assertThat(reactivada.getEstado()).isEqualTo(EstadoDeuda.EN_CONVENIO);
        assertThat(reactivada.getImporte()).isEqualByComparingTo("1000");
        assertThat(reactivada.getDeudaDesde()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(reactivada.getTributos()).extracting(Tributo::getCodigo).containsExactlyInAnyOrder("101", "3802");
    }

    private void sincronizarDeudaCompleta() {
        sincronizarFila("""
                {"CM":100202,"NUMERO_PADRON":4567,"IMPORTE_DEUDA":8000,
                 "DEUDA_DESDE":"2026-08-01","ULTIMO_VENCIMIENTO":"2026-09-01","ANIOS_DEUDA":"2026",
                 "PADTIPO":"COM","LOCALIDAD":"SAN CARLOS","BLOCK":"B","UNIDAD":"003",
                 "TRIBUTOS_DEUDA":"101, 3802","CONVENIO":"SI","DEUDA_FACTURAS":9000,
                 "SALDO_A_FAVOR":1000,"CREDITO_DESCONTADO":1000,"CANT_FACT_VENC":3,
                 "CANT_FACT_CON_DEUDA":2,"ANIO_DESDE":2026,"ANIO_HASTA":2026,"CANT_ANIOS_DEUDA":1}
                """, 2013);
    }

    private void sincronizarFila(String cuerpo, Integer anioMinimo) {
        var fila = json.readValue(cuerpo, GeoPagosFacturaPendienteDto.class);
        var pendientes = new IntendenciaApiResponse<>(List.of(fila), null);
        when(geoPagosClient.obtenerFacturasPendientes(any(), any())).thenReturn(pendientes);
        when(geoPagosClient.obtenerFacturasCanceladas(any(), any())).thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(geoPagosClient.obtenerContribuyentes()).thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        service.sincronizar(OrigenDatos.INTENDENCIA, anioMinimo);
        em.flush();
        em.clear();
    }

    /** Con período, un saldo cero en la respuesta no debería rechazar el resto de la sincronización. */
    @Test
    void servicioConUnSaldoCeroImportaLasDemasDeudas() {
        respuestaDeLaApi("periodo-pendientes.json");
        var filas = new java.util.ArrayList<>(pendientes("periodo-pendientes.json"));
        filas.add(pendientes("pendientes-dia1.json").get(3)); // CM 100204, importe 0
        when(geoPagosClient.obtenerFacturasPendientes(any(), any())).thenReturn(new IntendenciaApiResponse<>(filas, null));

        ResultadoSincronizacion r = service.sincronizar(OrigenDatos.INTENDENCIA);

        assertThat(r.deudasCreadas()).isEqualTo(1);
        assertThat(r.registrosOmitidos()).isEqualTo(1);
    }

    // ================================================================ Utilidades

    @Test
    void guardaLasCancelacionesSinDuplicarNiCambiarElEstadoPorUnCobro() {
        dia1();
        dia2();
        dia2();
        var pagina = org.springframework.data.domain.PageRequest.of(0, 20);
        var cobrosDelCm = cancelaciones.listar(new FiltroCancelaciones(OrigenDatos.INTENDENCIA, null, DIA_2, "100203", null), pagina);
        assertThat(cobrosDelCm.contenido()).extracting(Cancelacion::numeroCobro)
                .containsExactly("5501234", "5409876");
        assertThat(cobrosDelCm.totalElementos()).isEqualTo(2);
        var anulados = cancelaciones.listar(new FiltroCancelaciones(null, DIA_1, DIA_2, "100201", "anulado por error"), pagina);
        assertThat(anulados.contenido()).singleElement()
                .satisfies(c -> assertThat(cancelaciones.buscar(c.id())).contains(c));
        assertThat(count("SELECT count(*) FROM cancelaciones WHERE cm = '555555'")).isZero();
        assertThat(deudas.findAll().stream().filter(d -> d.getPadron().getCm().equals("100201")))
                .singleElement().satisfies(d -> assertThat(d.getEstado()).isNotEqualTo(EstadoDeuda.CANCELADA));

        var cobro = new GeoPagosFacturaCanceladaDto("100203", "8899", null, null,
                "5501234", DIA_1, java.math.BigDecimal.TEN, "PAGO");
        processor.procesar(List.of(), List.of(cobro), List.of(), DIA_2, DIA_2.atStartOfDay(), OrigenDatos.SUCIVE);
        var cobrosAmbosOrigenes = cancelaciones.listar(new FiltroCancelaciones(null, DIA_1, DIA_2, "5501234", null), pagina);
        assertThat(cobrosAmbosOrigenes.contenido()).extracting(Cancelacion::origen)
                .containsExactlyInAnyOrder(OrigenDatos.INTENDENCIA, OrigenDatos.SUCIVE);
    }

    @Test
    void guardaUnCobroPorCmAunqueLaApiRepitaSusFilasEnElMismoLote() {
        var importe = new java.math.BigDecimal("425.75");
        var sinTitular = new GeoPagosFacturaCanceladaDto("100203", "8899", null, null,
                "001234", DIA_1, importe, "PAGO");
        var titularB = new GeoPagosFacturaCanceladaDto("100203", "8899", "Titular B", "002",
                "001234", DIA_1, importe, "PAGO");
        var titularA = new GeoPagosFacturaCanceladaDto("100203", "8899", "Titular A", "001",
                "001234", DIA_1, importe, "PAGO");
        var otroCm = new GeoPagosFacturaCanceladaDto("100204", "8900", "Otro titular", "003",
                "001234", DIA_1, importe, "PAGO");
        var pagina = org.springframework.data.domain.PageRequest.of(0, 20);

        processor.procesar(List.of(), List.of(sinTitular, titularB, titularA, otroCm), List.of(),
                DIA_1, DIA_1.atStartOfDay(), OrigenDatos.INTENDENCIA);
        var primero = cancelaciones.listar(new FiltroCancelaciones(OrigenDatos.INTENDENCIA, null, null, "100203", null), pagina);
        assertThat(primero.contenido()).singleElement().satisfies(c -> {
            assertThat(c.numeroCobro()).isEqualTo("001234");
            assertThat(c.importeTotal()).isEqualByComparingTo(importe);
            assertThat(c.persona()).isEqualTo("Titular A");
            assertThat(c.documento()).isEqualTo("001");
        });

        processor.procesar(List.of(), List.of(otroCm, titularA, titularB, sinTitular), List.of(),
                DIA_2, DIA_2.atStartOfDay(), OrigenDatos.INTENDENCIA);
        assertThat(cancelaciones.listar(new FiltroCancelaciones(OrigenDatos.INTENDENCIA, null, null, "100203", null), pagina))
                .isEqualTo(primero);
        assertThat(cancelaciones.listar(new FiltroCancelaciones(OrigenDatos.INTENDENCIA, null, null, "001234", null), pagina).contenido())
                .extracting(Cancelacion::cm).containsExactlyInAnyOrder("100203", "100204");
    }

    @Test
    void exportaAmbosFormatosConLosFiltrosDeLaListaYElRolAnalista() throws Exception {
        var filas = new ArrayList<Cancelacion>();
        for (int i = 1; i <= 23; i++) {
            filas.add(new Cancelacion(null, OrigenDatos.SUCIVE, "00" + i, "000" + i, "001",
                    "=Titular", "0002", DIA_1, new BigDecimal("45.50"), "PAGO", DIA_1.atStartOfDay()));
        }
        filas.add(new Cancelacion(null, OrigenDatos.INTENDENCIA, "001", "0001", "001",
                "=Titular", "0002", DIA_1, BigDecimal.TEN, "PAGO", DIA_1.atStartOfDay()));
        filas.add(new Cancelacion(null, OrigenDatos.SUCIVE, "001", "fuera-motivo", "001",
                "=Titular", "0002", DIA_1, BigDecimal.TEN, "ANULADO", DIA_1.atStartOfDay()));
        filas.add(new Cancelacion(null, OrigenDatos.SUCIVE, "001", "fuera-fecha", "001",
                "=Titular", "0002", DIA_2, BigDecimal.TEN, "PAGO", DIA_2.atStartOfDay()));
        filas.add(new Cancelacion(null, OrigenDatos.SUCIVE, "001", "fuera-documento", "001",
                "Otra persona", "0003", DIA_1, BigDecimal.TEN, "PAGO", DIA_1.atStartOfDay()));
        cancelaciones.guardar(filas);

        var analista = user("usuario").roles("ANALISTA");
        mvc.perform(get("/api/cancelaciones").with(analista)
                        .param("origen", "SUCIVE").param("motivo", "pago").param("buscar", "0002")
                        .param("desde", DIA_1.toString()).param("hasta", DIA_1.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(23))
                .andExpect(jsonPath("$.contenido.length()").value(20));

        byte[] csv = mvc.perform(get("/api/cancelaciones/exportar").with(analista)
                        .param("origen", "SUCIVE").param("motivo", "pago").param("buscar", "0002")
                        .param("desde", DIA_1.toString()).param("hasta", DIA_1.toString())
                        .param("page", "1").param("size", "1").param("formato", "CSV"))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=cancelaciones.csv"))
                .andReturn().getResponse().getContentAsByteArray();
        String csvTexto = new String(csv, StandardCharsets.UTF_8);
        assertThat(csvTexto.lines()).hasSize(24);
        assertThat(csvTexto).startsWith("\uFEFF\"CM\"").contains("'=Titular", "45.50")
                .doesNotContain("Intendencia", "ANULADO", "fuera-fecha", "fuera-documento");

        byte[] excel = mvc.perform(get("/api/cancelaciones/exportar").with(analista)
                        .param("origen", "SUCIVE").param("motivo", "pago").param("buscar", "0002")
                        .param("desde", DIA_1.toString()).param("hasta", DIA_1.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=cancelaciones.xlsx"))
                .andReturn().getResponse().getContentAsByteArray();
        try (var libro = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            var hoja = libro.getSheetAt(0);
            assertThat(hoja.getLastRowNum()).isEqualTo(23);
            var fila = hoja.getRow(1);
            assertThat(fila.getCell(0).getStringCellValue()).isEqualTo("0023");
            assertThat(fila.getCell(3).getCellType()).isEqualTo(CellType.STRING);
            assertThat(fila.getCell(4).getStringCellValue()).isEqualTo("0002");
            assertThat(fila.getCell(8).getNumericCellValue()).isEqualTo(45.50);
        }
        for (String rol : List.of("ADMINISTRADOR", "NOTIFICADOR")) {
            mvc.perform(get("/api/cancelaciones/exportar").with(user("usuario").roles(rol)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/cancelaciones/exportar").with(analista).param("desde", DIA_2.toString())
                        .param("hasta", DIA_1.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void devuelveSoloMotivosPresentesSinRepeticionesParaElPanelDeExportacion() throws Exception {
        var motivos = java.util.Arrays.asList("PAGO", "pago", "ANULADO", "", null);
        var registros = new ArrayList<Cancelacion>();
        for (int i = 0; i < motivos.size(); i++) {
            registros.add(new Cancelacion(null, OrigenDatos.INTENDENCIA, "1", "C" + i,
                    null, null, null, DIA_1, BigDecimal.ONE, motivos.get(i), DIA_1.atStartOfDay()));
        }
        cancelaciones.guardar(registros);
        mvc.perform(get("/api/cancelaciones/motivos").with(user("analista").roles("ANALISTA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("ANULADO")).andExpect(jsonPath("$[1]").value("PAGO"));
        mvc.perform(get("/api/cancelaciones/motivos").with(user("notificador").roles("NOTIFICADOR")))
                .andExpect(status().isForbidden());
    }

    ResultadoSincronizacion dia1() {
        return procesar(pendientes("pendientes-dia1.json"), List.of(), contribuyentes(), DIA_1);
    }

    /** Día 2 sin respuesta de contribuyentes-geopagos (null = no disponible). */
    ResultadoSincronizacion dia2() {
        return procesar(pendientes("pendientes-dia2.json"), canceladas(), null, DIA_2);
    }

    ResultadoSincronizacion procesar(List<GeoPagosFacturaPendienteDto> pendientes,
            List<GeoPagosFacturaCanceladaDto> canceladas, List<GeoPagosContribuyenteDto> contribuyentes, LocalDate hoy) {
        ResultadoSincronizacion r = processor.procesar(pendientes, canceladas, contribuyentes, hoy,
                LocalDateTime.now(clock), OrigenDatos.INTENDENCIA);
        em.flush();
        em.clear();
        return r;
    }

    void respuestaDeLaApi(String pendientes) {
        when(geoPagosClient.obtenerFacturasPendientes(any(), any()))
                .thenReturn(new IntendenciaApiResponse<>(pendientes(pendientes), null));
        when(geoPagosClient.obtenerFacturasCanceladas(any(), any())).thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(geoPagosClient.obtenerContribuyentes()).thenReturn(new IntendenciaApiResponse<>(List.of(), null));
    }

    List<GeoPagosFacturaPendienteDto> pendientes(String archivo) {
        return leer(archivo, new TypeReference<IntendenciaApiResponse<GeoPagosFacturaPendienteDto>>() {});
    }

    List<GeoPagosFacturaCanceladaDto> canceladas() {
        return leer("canceladas-dia2.json", new TypeReference<IntendenciaApiResponse<GeoPagosFacturaCanceladaDto>>() {});
    }

    List<GeoPagosContribuyenteDto> contribuyentes() {
        return leer("contribuyentes.json", new TypeReference<IntendenciaApiResponse<GeoPagosContribuyenteDto>>() {});
    }

    <T> List<T> leer(String archivo, TypeReference<IntendenciaApiResponse<T>> tipo) {
        try (InputStream in = getClass().getResourceAsStream("/sincronizacion/casos-reales/" + archivo)) {
            return json.readValue(in, tipo).getData();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    Deuda deuda(String cm) {
        Deuda deuda = deudas.findByPadronCmAndPadronOrigen(cm, OrigenDatos.INTENDENCIA).orElseThrow();
        deuda.getTributos().size();
        deuda.getPadron().getContribuyente().getNombre();
        if (deuda.getSegmentoMora() != null) deuda.getSegmentoMora().getNombre();
        return deuda;
    }

    List<String> cms() {
        return jdbc.queryForList("SELECT cm FROM padrones", String.class);
    }

    Long contribuyenteId(String documento) {
        return jdbc.queryForObject("SELECT id FROM contribuyentes WHERE trim(documento) = ?", Long.class, documento);
    }

    List<String> contactos(Long contribuyente) {
        return jdbc.queryForList("SELECT tipo_contacto || '|' || valor || '|' || origen || '|' || es_de_contribuyente "
                + "FROM contactos WHERE contribuyente_id = ?", String.class, contribuyente);
    }

    long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
