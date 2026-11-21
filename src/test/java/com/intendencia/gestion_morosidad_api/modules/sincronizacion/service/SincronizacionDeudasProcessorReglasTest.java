package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.intendencia.gestion_morosidad_api.modules.cancelacion.CancelacionRepository;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaCanceladaDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService.Dato;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.ConfiguracionSegmento;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ConfiguracionSegmentoService;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ReglasSegmentacion;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion;
import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import com.intendencia.gestion_morosidad_api.modules.tributo.repository.TributoRepository;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Reglas de README "Sincronización con GeoPagos → Reglas" probadas contra el procesador con
 * repositorios simulados. Complementa {@link SincronizacionDeudasProcessorTest}: acá se cubren
 * estados, protección ante respuestas vacías, conciliación, segmentos, cobros e identidad.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SincronizacionDeudasProcessorReglasTest {

    static final LocalDate HOY = LocalDate.of(2026, 9, 24);
    static final LocalDateTime INICIO = LocalDateTime.of(2026, 9, 24, 11, 59);
    /** 15:00Z en Montevideo (UTC-3). */
    static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 24, 12, 0);

    @Mock DeudaRepository deudaRepository;
    @Mock PadronRepository padronRepository;
    @Mock ContribuyenteRepository contribuyenteRepository;
    @Mock SincronizacionContactosService contactos;
    @Mock TributoRepository tributoRepository;
    @Mock ConfiguracionSegmentoService configuracionSegmentoService;
    SincronizacionDeudasProcessor processor;

    @Mock CancelacionRepository cancelaciones;

    @BeforeEach
    void preparar() {
        processor = new SincronizacionDeudasProcessor(deudaRepository, padronRepository,
                contribuyenteRepository, contactos, tributoRepository, configuracionSegmentoService,
                cancelaciones, Clock.fixed(Instant.parse("2026-09-24T15:00:00Z"), ZoneId.of("America/Montevideo")));
        when(configuracionSegmentoService.reglasVigentes()).thenReturn(reglasBase());
    }

    // ---------------------------------------------------------------- Protección de vacíos

    @ParameterizedTest
    @EnumSource(OrigenDatos.class)
    void conDeudasActivasNoAplicaNingunCambio(OrigenDatos origen) {
        Deuda activa = existente("123", EstadoDeuda.PENDIENTE, origen);
        cargar(origen, activa);
        when(deudaRepository.countByPadronOrigenAndEstadoNot(origen, EstadoDeuda.CANCELADA)).thenReturn(1L);

        assertThatThrownBy(() -> processor.procesar(List.of(), List.of(), List.of(), HOY, INICIO, origen))
                .isInstanceOf(IntegracionExternaException.class)
                .hasMessageContaining(origen.proveedor())
                .hasMessageContaining("no se aplicaron cambios");

        assertThat(activa.getEstado()).isEqualTo(EstadoDeuda.PENDIENTE);
        assertThat(activa.getFechaSincronizacion()).isNull();
        verify(deudaRepository, never()).saveAll(any());
        verify(contactos, never()).sincronizar(any());
    }

    @Test
    void sinDeudasActivasEsUnResultadoValido() {
        Deuda cancelada = existente("123", EstadoDeuda.CANCELADA, OrigenDatos.INTENDENCIA);
        cargar(OrigenDatos.INTENDENCIA, cancelada);
        when(deudaRepository.countByPadronOrigenAndEstadoNot(OrigenDatos.INTENDENCIA, EstadoDeuda.CANCELADA)).thenReturn(0L);

        var resultado = procesar(List.of());

        assertThat(resultado.deudasCanceladas()).isZero();
        assertThat(cancelada.getEstado()).isEqualTo(EstadoDeuda.CANCELADA);
    }

    @Test
    void conPeriodoFiltradoUnaRespuestaVaciaNoEsError() {
        when(deudaRepository.countByPadronOrigenAndEstadoNot(any(), any())).thenReturn(5L);

        var resultado = processor.procesar(List.of(), List.of(), List.of(), HOY, INICIO, OrigenDatos.INTENDENCIA, 2020);

        assertThat(resultado.pendientesRecibidas()).isZero();
        verify(deudaRepository, never()).countByPadronOrigenAndEstadoNot(any(), any());
    }

    @Test
    void conPeriodoUnaRespuestaVaciaConDeudasActivasDelPeriodoNoAplicaCambios() {
        Deuda activa = existente("123", EstadoDeuda.PENDIENTE, OrigenDatos.INTENDENCIA);
        activa.setDeudaDesde(LocalDate.of(2026, 3, 10));
        cargar(OrigenDatos.INTENDENCIA, activa);

        assertThatThrownBy(() -> processor.procesar(List.of(), List.of(), List.of(), HOY, INICIO, OrigenDatos.INTENDENCIA, 2013))
                .isInstanceOf(IntegracionExternaException.class)
                .hasMessageContaining("no se aplicaron cambios");

        assertThat(activa.isEnPeriodoGestion()).isTrue();
        assertThat(activa.getFechaSincronizacion()).isNull();
        verify(deudaRepository, never()).saveAll(any());
    }

    @ParameterizedTest(name = "estado={0}, deudaDesde={1}")
    @CsvSource({"CANCELADA, 2026-03-10", "PENDIENTE, 2010-05-01", "PENDIENTE, "})
    void conPeriodoUnaRespuestaVaciaSinDeudasActivasDelPeriodoEsValida(EstadoDeuda estado, LocalDate desde) {
        Deuda deuda = existente("123", estado, OrigenDatos.INTENDENCIA);
        deuda.setDeudaDesde(desde);
        cargar(OrigenDatos.INTENDENCIA, deuda);

        var resultado = processor.procesar(List.of(), List.of(), List.of(), HOY, INICIO, OrigenDatos.INTENDENCIA, 2013);

        assertThat(resultado.deudasCanceladas()).isZero();
        assertThat(deuda.isEnPeriodoGestion()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0.00", "-10.00"})
    void conPeriodoUnSaldoNoPositivoNoRechazaLaSincronizacion(String importe) {
        Deuda pagada = existente("200", EstadoDeuda.PENDIENTE, OrigenDatos.INTENDENCIA);
        cargar(OrigenDatos.INTENDENCIA, pagada);

        var resultado = processor.procesar(List.of(fila("1").build(), fila("200").importe(importe).build()),
                List.of(), List.of(), HOY, INICIO, OrigenDatos.INTENDENCIA, 2013);

        assertThat(resultado.deudasCreadas()).isEqualTo(1);
        assertThat(resultado.registrosOmitidos()).isEqualTo(1);
        assertThat(resultado.deudasCanceladas()).isEqualTo(1);
        assertThat(pagada.getEstado()).isEqualTo(EstadoDeuda.CANCELADA);
    }

    @Test
    void conPeriodoUnCmRepetidoSigueRechazandoLaRespuesta() {
        assertThatThrownBy(() -> processor.procesar(List.of(fila("1").build(), fila("1").build()),
                List.of(), List.of(), HOY, INICIO, OrigenDatos.INTENDENCIA, 2013))
                .isInstanceOf(IntegracionExternaException.class).hasMessageContaining("incompletos");
        verify(deudaRepository, never()).saveAll(any());
    }

    // ---------------------------------------------------------------- Estados (CU 2.17)

    @ParameterizedTest(name = "{0} + convenio={1} → {2}")
    @CsvSource({
            "PENDIENTE,   NO, PENDIENTE",
            "PENDIENTE,   SI, EN_CONVENIO",
            "EN_GESTION,  NO, EN_GESTION",
            "EN_GESTION,  SI, EN_CONVENIO",
            "EN_CONVENIO, NO, PENDIENTE",
            "EN_CONVENIO, SI, EN_CONVENIO",
            "CANCELADA,   NO, PENDIENTE",
            "CANCELADA,   SI, EN_CONVENIO",
    })
    void deudaQueSigueEnPendientes(EstadoDeuda anterior, String convenio, EstadoDeuda esperado) {
        Deuda deuda = existente("123", anterior, OrigenDatos.INTENDENCIA);
        cargar(OrigenDatos.INTENDENCIA, deuda);

        var resultado = procesar(List.of(fila("123").convenio(convenio).build()));

        assertThat(deuda.getEstado()).isEqualTo(esperado);
        assertThat(deuda.isConvenio()).isEqualTo("SI".equals(convenio));
        assertThat(deuda.getId()).isEqualTo(123L);
        assertThat(resultado.deudasActualizadas()).isEqualTo(1);
        assertThat(resultado.deudasCreadas()).isZero();
        assertThat(deuda.getFechaSincronizacion()).isEqualTo(AHORA);
    }

    @Test
    void convenioSeComparaSinMayusculasNiEspacios() {
        Deuda deuda = existente("123", EstadoDeuda.PENDIENTE, OrigenDatos.INTENDENCIA);
        cargar(OrigenDatos.INTENDENCIA, deuda);

        procesar(List.of(fila("123").convenio("  si ").build()));

        assertThat(deuda.getEstado()).isEqualTo(EstadoDeuda.EN_CONVENIO);
    }

    @Test
    void deudaNuevaNaceComoPendiente() {
        var resultado = procesar(List.of(fila("900").build()));

        assertThat(nuevasGuardadas()).singleElement().satisfies(d -> {
            assertThat(d.getEstado()).isEqualTo(EstadoDeuda.PENDIENTE);
            assertThat(d.getPadron().getOrigen()).isEqualTo(OrigenDatos.INTENDENCIA);
            assertThat(d.getFechaSincronizacion()).isEqualTo(AHORA);
        });
        assertThat(resultado.deudasCreadas()).isEqualTo(1);
    }

    @Test
    void cancelaLasAusentesUnaSolaVezYNoRecuentaLasYaCanceladas() {
        Deuda presente = existente("1", EstadoDeuda.PENDIENTE, OrigenDatos.INTENDENCIA);
        Deuda ausenteEnGestion = existente("2", EstadoDeuda.EN_GESTION, OrigenDatos.INTENDENCIA);
        Deuda yaCancelada = existente("3", EstadoDeuda.CANCELADA, OrigenDatos.INTENDENCIA);
        LocalDateTime anterior = LocalDateTime.of(2026, 1, 1, 0, 0);
        yaCancelada.setFechaSincronizacion(anterior);
        cargar(OrigenDatos.INTENDENCIA, presente, ausenteEnGestion, yaCancelada);

        var resultado = procesar(List.of(fila("1").build()));

        // Una deuda pagada sale de pendientes aunque estuviera en gestión.
        assertThat(ausenteEnGestion.getEstado()).isEqualTo(EstadoDeuda.CANCELADA);
        assertThat(ausenteEnGestion.getSegmentoMora()).isNull();
        assertThat(ausenteEnGestion.getFechaSincronizacion()).isEqualTo(AHORA);
        assertThat(yaCancelada.getFechaSincronizacion()).isEqualTo(anterior);
        assertThat(resultado.deudasCanceladas()).isEqualTo(1);
    }

    @Test
    void saldoCeroSobreDeudaYaCanceladaNoSeCuentaDosVeces() {
        Deuda cancelada = existente("123", EstadoDeuda.CANCELADA, OrigenDatos.INTENDENCIA);
        cargar(OrigenDatos.INTENDENCIA, cancelada);

        var resultado = procesar(List.of(fila("123").importe("0").build()));

        assertThat(resultado.deudasCanceladas()).isZero();
        assertThat(resultado.registrosOmitidos()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- Conciliación por CM

    @Test
    void cmRepetidoSeAplicaUnaVezYLaSegundaFilaSeOmite() {
        var resultado = procesar(List.of(
                fila("123").importe("100").build(),
                fila("123").importe("999").build()));

        assertThat(nuevasGuardadas()).singleElement()
                .satisfies(d -> assertThat(d.getImporte()).isEqualByComparingTo("100"));
        assertThat(resultado.registrosOmitidos()).isEqualTo(1);
        assertThat(resultado.advertencias()).anyMatch(a -> a.contains("CM 123 repetido"));
    }

    @Test
    void filaSinCmSeOmite() {
        var resultado = procesar(List.of(fila(null).build(), fila("   ").build(), fila("5").build()));

        assertThat(resultado.registrosOmitidos()).isEqualTo(2);
        assertThat(resultado.deudasCreadas()).isEqualTo(1);
        assertThat(resultado.advertencias()).filteredOn("Registro sin CM"::equals).hasSize(2);
    }

    @Test
    void cmNumericoYTextoIdentificanAlMismoPadron() {
        Deuda deuda = existente("123", EstadoDeuda.PENDIENTE, OrigenDatos.INTENDENCIA);
        cargar(OrigenDatos.INTENDENCIA, deuda);

        // La API puede enviar el CM como número entero, decimal o texto con espacios.
        var resultado = procesar(List.of(fila(new BigDecimal("123.0")).build()));

        assertThat(resultado.deudasCreadas()).isZero();
        assertThat(resultado.deudasActualizadas()).isEqualTo(1);
        assertThat(resultado.deudasCanceladas()).isZero();
    }

    @Test
    void numeroDePadronSeRepiteEnDistintasLocalidadesSinMezclarse() {
        var resultado = procesar(List.of(
                fila("1001").padron("4567").localidad("CIUDAD").build(),
                fila("2002").padron("4567").localidad("BALNEARIO").build()));

        assertThat(resultado.deudasCreadas()).isEqualTo(2);
        assertThat(nuevasGuardadas()).extracting(d -> d.getPadron().getCm())
                .containsExactlyInAnyOrder("1001", "2002");
    }

    @Test
    void advertenciasSeLimitanPeroLosOmitidosSeCuentanTodos() {
        List<GeoPagosFacturaPendienteDto> filas = new ArrayList<>();
        IntStream.range(0, 150).forEach(i -> filas.add(fila(null).build()));
        filas.add(fila("1").build());

        var resultado = procesar(filas);

        assertThat(resultado.registrosOmitidos()).isEqualTo(150);
        assertThat(resultado.advertencias()).hasSize(100);
    }

    @Test
    void resultadoUsaElRelojDeUruguay() {
        var resultado = procesar(List.of(fila("1").build()));

        assertThat(resultado.inicio()).isEqualTo(INICIO);
        assertThat(resultado.fin()).isEqualTo(AHORA);
        assertThat(resultado.pendientesRecibidas()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- Tributos

    @Test
    void mismoCodigoNuevoSeCreaUnaSolaVezAunqueLoUsenVariasDeudas() {
        procesar(List.of(fila("1").tributos("101, 3802").build(), fila("2").tributos("3802,101,,101").build()));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<Tributo>> captor = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(tributoRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Tributo::getCodigo).containsExactlyInAnyOrder("101", "3802");
        List<Deuda> nuevas = nuevasGuardadas();
        assertThat(nuevas.get(0).getTributos()).containsExactlyInAnyOrderElementsOf(nuevas.get(1).getTributos());
    }

    @Test
    void reutilizaTributosExistentesDelOrigenYNoReescribeSiNoCambian() {
        Tributo t101 = tributo(1L, "101");
        Tributo t201 = tributo(2L, "201");
        when(tributoRepository.findAllByOrigen(OrigenDatos.INTENDENCIA)).thenReturn(List.of(t101, t201));
        Deuda deuda = existente("123", EstadoDeuda.PENDIENTE, OrigenDatos.INTENDENCIA);
        deuda.getTributos().add(t101);
        var coleccion = deuda.getTributos();
        cargar(OrigenDatos.INTENDENCIA, deuda);

        procesar(List.of(fila("123").tributos(" 101 ").build()));
        assertThat(deuda.getTributos()).isSameAs(coleccion).containsExactly(t101);

        procesar(List.of(fila("123").tributos("201").build()));
        assertThat(deuda.getTributos()).containsExactly(t201);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<Tributo>> captor = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(tributoRepository, org.mockito.Mockito.times(2)).saveAll(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(nuevos -> assertThat(nuevos).isEmpty());
    }

    // ---------------------------------------------------------------- Segmentos de mora

    @ParameterizedTest(name = "{0} días → segmento {1}")
    @CsvSource({"0, 1", "90, 1", "91, 2", "365, 2", "366, 3", "4000, 3"})
    void asignaSegmentoBaseSegunDiasDesdeDeudaDesde(int dias, int orden) {
        procesar(List.of(fila("1").deudaDesde(HOY.minusDays(dias)).build()));

        assertThat(nuevasGuardadas()).singleElement()
                .satisfies(d -> assertThat(d.getSegmentoMora().getOrden()).isEqualTo(orden));
    }

    @Test
    void sinDeudaDesdeNoTieneSegmento() {
        procesar(List.of(fila("1").deudaDesde(null).build()));

        assertThat(nuevasGuardadas()).singleElement()
                .satisfies(d -> assertThat(d.getSegmentoMora()).isNull());
    }

    @Test
    void padTipoConRangosPropiosUsaEsosRangosYElRestoLaBase() {
        var configuraciones = new ArrayList<ConfiguracionSegmento>();
        configuraciones.addAll(configuracionesBase());
        configuraciones.add(rango(segmento(1), "COM", 0, 10));
        configuraciones.add(rango(segmento(2), "COM", 11, 20));
        configuraciones.add(rango(segmento(3), "COM", 21, null));
        when(configuracionSegmentoService.reglasVigentes()).thenReturn(new ReglasSegmentacion(configuraciones));

        procesar(List.of(
                fila("1").padtipo("COM").deudaDesde(HOY.minusDays(30)).build(),
                fila("2").padtipo("CIU").deudaDesde(HOY.minusDays(30)).build()));

        List<Deuda> nuevas = nuevasGuardadas();
        assertThat(nuevas.get(0).getSegmentoMora().getOrden()).isEqualTo(3);
        assertThat(nuevas.get(1).getSegmentoMora().getOrden()).isEqualTo(1);
    }

    @Test
    void recalculaElSegmentoDeUnaDeudaExistenteConLaFechaNueva() {
        Deuda deuda = existente("123", EstadoDeuda.EN_GESTION, OrigenDatos.INTENDENCIA);
        deuda.setSegmentoMora(segmento(1));
        cargar(OrigenDatos.INTENDENCIA, deuda);

        procesar(List.of(fila("123").deudaDesde(HOY.minusDays(400)).build()));

        assertThat(deuda.getSegmentoMora().getOrden()).isEqualTo(3);
        assertThat(deuda.getEstado()).isEqualTo(EstadoDeuda.EN_GESTION);
    }

    // ---------------------------------------------------------------- Cobros

    @Test
    void conservaElCobroMasRecienteSinImportarElOrden() {
        Deuda deuda = existente("123", EstadoDeuda.PENDIENTE, OrigenDatos.INTENDENCIA);
        cargar(OrigenDatos.INTENDENCIA, deuda);

        processor.procesar(List.of(fila("123").build()), List.of(
                cobro("123", LocalDate.of(2026, 9, 10), "500.00", "PAGO"),
                cobro("123", LocalDate.of(2026, 8, 1), "100.00", "VIEJO"),
                cobro("123", null, "1.00", "SIN FECHA")), List.of(), HOY, INICIO);

        assertThat(deuda.getFechaUltimoCobro()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(deuda.getImporteUltimoCobro()).isEqualByComparingTo("500.00");
        assertThat(deuda.getMotivoUltimoCobro()).isEqualTo("PAGO");
    }

    @Test
    void cobroDeUnCmDesconocidoNoCreaNada() {
        var resultado = processor.procesar(List.of(fila("1").build()),
                List.of(cobro("777", LocalDate.of(2026, 9, 10), "50.00", null)), List.of(), HOY, INICIO);

        assertThat(resultado.cobrosRecibidos()).isEqualTo(1);
        assertThat(resultado.deudasCreadas()).isEqualTo(1);
        assertThat(nuevasGuardadas()).singleElement()
                .satisfies(d -> assertThat(d.getFechaUltimoCobro()).isNull());
    }

    @Test
    void motivoLargoSeRecortaAlLargoDeLaColumna() {
        Deuda deuda = existente("123", EstadoDeuda.PENDIENTE, OrigenDatos.INTENDENCIA);
        cargar(OrigenDatos.INTENDENCIA, deuda);

        processor.procesar(List.of(fila("123").build()),
                List.of(cobro("123", LocalDate.of(2026, 9, 10), "50.00", "X".repeat(80))), List.of(), HOY, INICIO);

        assertThat(deuda.getMotivoUltimoCobro()).hasSize(60);
    }

    // ---------------------------------------------------------------- Identidad y contactos

    @Test
    void mismoDocumentoEnVariosPadronesCreaUnSoloContribuyente() {
        procesar(List.of(
                fila("1").documento("1.234.567-8").persona("Ana Pérez").build(),
                fila("2").documento(" 1.234.567-8 ").persona("ANA PEREZ").build()));

        List<Deuda> nuevas = nuevasGuardadas();
        assertThat(nuevas.get(0).getPadron().getContribuyente())
                .isSameAs(nuevas.get(1).getPadron().getContribuyente());
        assertThat(contribuyentesGuardados()).hasSize(1);
    }

    @Test
    void documentoExistenteEnLaBaseVinculaElPadronNuevoAEsaPersona() {
        Contribuyente ana = new Contribuyente();
        ana.setId(10L);
        ana.setNombre("Ana Pérez");
        ana.setDocumento("12345678");
        when(contribuyenteRepository.findAll()).thenReturn(List.of(ana));

        procesar(List.of(fila("1").documento(12345678).persona("Ana Pérez").build()));

        assertThat(nuevasGuardadas()).singleElement()
                .satisfies(d -> assertThat(d.getPadron().getContribuyente()).isSameAs(ana));
        assertThat(contribuyentesGuardados()).isEmpty();
    }

    @Test
    void sinNombreNiDocumentoUsaMarcadorYNoUneDistintosPadrones() {
        procesar(List.of(fila("1").persona(null).documento(null).build(),
                fila("2").persona("  ").documento("").build()));

        List<Contribuyente> nuevos = contribuyentesGuardados();
        assertThat(nuevos).hasSize(2).allSatisfy(c -> {
            assertThat(c.getNombre()).isEqualTo("(sin nombre)");
            assertThat(c.getDocumento()).isNull();
        });
    }

    @Test
    void domicilioSeArmaConCalleYPuertaAunqueFalteUnaParte() {
        procesar(List.of(
                fila("1").direccion("18 DE JULIO").puerta(null).build(),
                fila("2").direccion(null).puerta(1234).build()));

        assertThat(contactosEnviados())
                .filteredOn(d -> d.tipo() == com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto.DOMICILIO)
                .extracting(Dato::valor).containsExactly("18 DE JULIO", "1234");
    }

    @Test
    void suciveNoImportaContactosDelUltimoGeoPagoYUsaFuentesGeoPat() {
        var contribuyente = new GeoPagosContribuyenteDto("1", "4567", "PAT", null, null, null,
                "Ana", "099111222", "ana@test.uy", "Otro", "098000000", "otro@test.uy", null, "SBA1234");

        processor.procesar(List.of(fila("1").padtipo("PAT").build()), List.of(), List.of(contribuyente),
                HOY, INICIO, OrigenDatos.SUCIVE);

        List<Dato> enviados = contactosEnviados();
        assertThat(enviados).extracting(Dato::origen).doesNotContain(SincronizacionContactosService.GEOPAGOS,
                SincronizacionContactosService.PERSONAS, SincronizacionContactosService.PENDIENTES);
        assertThat(enviados).filteredOn(d -> d.origen().equals(SincronizacionContactosService.GEOPAT_PERSONAS))
                .extracting(Dato::valor).containsExactly("099111222", "ana@test.uy");
        assertThat(nuevasGuardadas()).singleElement()
                .satisfies(d -> assertThat(d.getPadron().getMatricula()).isEqualTo("SBA1234"));
    }

    // ================================================================ Utilidades

    ResultadoSincronizacion procesar(List<GeoPagosFacturaPendienteDto> filas) {
        return processor.procesar(filas, List.of(), List.of(), HOY, INICIO);
    }

    void cargar(OrigenDatos origen, Deuda... deudas) {
        when(deudaRepository.findAllConPadronPorOrigen(origen)).thenReturn(List.of(deudas));
        when(padronRepository.findAllByOrigen(origen)).thenReturn(
                java.util.Arrays.stream(deudas).map(Deuda::getPadron).toList());
    }

    static Deuda existente(String cm, EstadoDeuda estado, OrigenDatos origen) {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setId(Long.parseLong(cm) + 100);
        contribuyente.setNombre("Titular " + cm);
        Padron padron = new Padron();
        padron.setCm(cm);
        padron.setNumeroPadron("P" + cm);
        padron.setOrigen(origen);
        padron.setContribuyente(contribuyente);
        Deuda deuda = new Deuda();
        deuda.setId(Long.parseLong(cm));
        deuda.setPadron(padron);
        deuda.setEstado(estado);
        deuda.setImporte(new BigDecimal("100.00"));
        return deuda;
    }

    List<Deuda> nuevasGuardadas() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<Deuda>> captor = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(deudaRepository, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
        List<Deuda> lista = new ArrayList<>();
        captor.getValue().forEach(lista::add);
        return lista;
    }

    List<Contribuyente> contribuyentesGuardados() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<Contribuyente>> captor = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(contribuyenteRepository).saveAll(captor.capture());
        List<Contribuyente> lista = new ArrayList<>();
        captor.getValue().forEach(lista::add);
        return lista;
    }

    List<Dato> contactosEnviados() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<Dato>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(contactos).sincronizar(captor.capture());
        return captor.getValue();
    }

    static GeoPagosFacturaCanceladaDto cobro(String cm, LocalDate fecha, String importe, String motivo) {
        return new GeoPagosFacturaCanceladaDto(cm, null, null, null, 1, fecha, new BigDecimal(importe), motivo);
    }

    static Tributo tributo(Long id, String codigo) {
        Tributo tributo = new Tributo();
        tributo.setId(id);
        tributo.setCodigo(codigo);
        tributo.setOrigen(OrigenDatos.INTENDENCIA);
        return tributo;
    }

    static ReglasSegmentacion reglasBase() {
        return new ReglasSegmentacion(configuracionesBase());
    }

    /** Rangos de la migración V202609241700: 0-90, 91-365, 366+. */
    static List<ConfiguracionSegmento> configuracionesBase() {
        return List.of(rango(segmento(1), null, 0, 90), rango(segmento(2), null, 91, 365),
                rango(segmento(3), null, 366, null));
    }

    static SegmentoMora segmento(int orden) {
        SegmentoMora segmento = new SegmentoMora();
        segmento.setId((long) orden);
        segmento.setOrden(orden);
        segmento.setNombre(switch (orden) {
            case 1 -> "Mora Temprana";
            case 2 -> "Mora Inicial";
            default -> "Mora Tardía";
        });
        return segmento;
    }

    static ConfiguracionSegmento rango(SegmentoMora segmento, String tipoPadron, int desde, Integer hasta) {
        ConfiguracionSegmento configuracion = new ConfiguracionSegmento();
        configuracion.setSegmento(segmento);
        configuracion.setTipoPadron(tipoPadron);
        configuracion.setDiasDesde(desde);
        configuracion.setDiasHasta(hasta);
        return configuracion;
    }

    static Fila fila(Object cm) {
        return new Fila(cm);
    }

    /** Fila de /facturas/pendientes con valores por defecto válidos. */
    static final class Fila {
        Object cm;
        Object padron = "4567";
        String padtipo = "CIU";
        String localidad = "CIUDAD";
        String persona = "Persona de prueba";
        Object documento;
        String direccion;
        Object puerta;
        String convenio = "NO";
        String tributos = "101";
        BigDecimal importe = new BigDecimal("100.00");
        LocalDate deudaDesde = HOY.minusDays(30);

        Fila(Object cm) { this.cm = cm; }
        Fila padron(Object v) { padron = v; return this; }
        Fila padtipo(String v) { padtipo = v; return this; }
        Fila localidad(String v) { localidad = v; return this; }
        Fila persona(String v) { persona = v; return this; }
        Fila documento(Object v) { documento = v; return this; }
        Fila direccion(String v) { direccion = v; return this; }
        Fila puerta(Object v) { puerta = v; return this; }
        Fila convenio(String v) { convenio = v; return this; }
        Fila tributos(String v) { tributos = v; return this; }
        Fila importe(String v) { importe = v == null ? null : new BigDecimal(v); return this; }
        Fila deudaDesde(LocalDate v) { deudaDesde = v; return this; }

        GeoPagosFacturaPendienteDto build() {
            return new GeoPagosFacturaPendienteDto(cm, padron, null, null, padtipo, localidad, persona,
                    documento, null, null, direccion, puerta, convenio, tributos, importe,
                    deudaDesde, deudaDesde, deudaDesde == null ? null : String.valueOf(deudaDesde.getYear()));
        }
    }
}
