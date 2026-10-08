package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaCanceladaDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService.Dato;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ConfiguracionSegmentoService;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ReglasSegmentacion;
import com.intendencia.gestion_morosidad_api.modules.tributo.repository.TributoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SincronizacionDeudasProcessorTest {

    @Mock private DeudaRepository deudaRepository;
    @Mock private PadronRepository padronRepository;
    @Mock private ContribuyenteRepository contribuyenteRepository;
    @Mock private SincronizacionContactosService sincronizacionContactosService;
    @Mock private TributoRepository tributoRepository;
    @Mock private ConfiguracionSegmentoService configuracionSegmentoService;
    @InjectMocks private SincronizacionDeudasProcessor processor;

    @Test
    void actualizaElContribuyenteDelPadronExistenteSinDuplicarlo() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Nombre anterior");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setNumeroPadron("4567");
        padron.setContribuyente(contribuyente);
        when(padronRepository.findAll()).thenReturn(List.of(padron));
        when(configuracionSegmentoService.reglasVigentes()).thenReturn(new ReglasSegmentacion(List.of()));
        GeoPagosFacturaPendienteDto fila = new GeoPagosFacturaPendienteDto(
                123, 4567, null, null, "COM", null, "Ana Pérez", "1234567-8",
                null, null, null, null, "NO", null, new BigDecimal("100.00"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), "2026");

        var resultado = processor.procesar(List.of(fila), List.of(),
                LocalDate.of(2026, 9, 24), LocalDateTime.of(2026, 9, 24, 12, 0));

        assertThat(resultado.deudasCreadas()).isEqualTo(1);
        assertThat(padron.getContribuyente()).isSameAs(contribuyente);
        assertThat(contribuyente.getNombre()).isEqualTo("Ana Pérez");
        assertThat(contribuyente.getDocumento()).isEqualTo("1234567-8");
        verify(contribuyenteRepository).saveAll(argThat(nuevos -> !nuevos.iterator().hasNext()));
    }

    @Test
    void importaTelefonoCorreoYDomicilioDeFacturasPendientes() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setId(1L);
        contribuyente.setNombre("Ana Pérez");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setNumeroPadron("4567");
        padron.setContribuyente(contribuyente);
        when(padronRepository.findAll()).thenReturn(List.of(padron));
        when(configuracionSegmentoService.reglasVigentes()).thenReturn(new ReglasSegmentacion(List.of()));
        GeoPagosFacturaPendienteDto fila = new GeoPagosFacturaPendienteDto(
                123, 4567, null, null, "COM", null, "Ana Pérez", "1234567-8",
                99123456, "ana@example.test", "Calle Central", "0010", "NO", null,
                new BigDecimal("100.00"), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1), "2026");

        processor.procesar(List.of(fila), List.of(),
                LocalDate.of(2026, 9, 24), LocalDateTime.of(2026, 9, 24, 12, 0));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<Dato>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(sincronizacionContactosService).sincronizar(captor.capture());
        List<Dato> contactos = captor.getValue();
        assertThat(contactos).extracting(Dato::tipo)
                .containsExactly(TipoContacto.TELEFONO, TipoContacto.EMAIL, TipoContacto.DOMICILIO);
        assertThat(contactos).extracting(Dato::valor)
                .containsExactly("99123456", "ana@example.test", "Calle Central 0010");
        assertThat(contactos).allSatisfy(dato -> {
            assertThat(dato.contribuyente()).isSameAs(contribuyente);
            assertThat(dato.origen()).isEqualTo(SincronizacionContactosService.PENDIENTES);
            assertThat(dato.esDeContribuyente()).isTrue();
        });
    }

    @Test
    void cobroCompletaDocumentoSinDeudaPendiente() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Ana Pérez");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setNumeroPadron("4567");
        padron.setContribuyente(contribuyente);
        when(padronRepository.findAll()).thenReturn(List.of(padron));
        GeoPagosFacturaCanceladaDto cobro = new GeoPagosFacturaCanceladaDto(
                123, 4567, "Ana Pérez", "1234567-8", 9876,
                LocalDate.of(2026, 9, 2), new BigDecimal("100.00"));

        processor.procesar(List.of(), List.of(cobro),
                LocalDate.of(2026, 9, 24), LocalDateTime.of(2026, 9, 24, 12, 0));

        assertThat(contribuyente.getDocumento()).isEqualTo("1234567-8");
        verify(sincronizacionContactosService).sincronizar(List.of());
    }

    @Test
    void cobroNoReemplazaElDocumentoDeUnaFacturaPendienteActual() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Ana Pérez");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setNumeroPadron("4567");
        padron.setContribuyente(contribuyente);
        when(padronRepository.findAll()).thenReturn(List.of(padron));
        when(configuracionSegmentoService.reglasVigentes()).thenReturn(new ReglasSegmentacion(List.of()));
        GeoPagosFacturaPendienteDto pendiente = new GeoPagosFacturaPendienteDto(
                123, 4567, null, null, "COM", null, "Ana Pérez", "actual-123",
                null, null, null, null, "NO", null, new BigDecimal("100.00"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), "2026");
        GeoPagosFacturaCanceladaDto cobro = new GeoPagosFacturaCanceladaDto(
                123, 4567, "Persona anterior", "anterior-456", 9876,
                LocalDate.of(2026, 9, 2), new BigDecimal("100.00"));

        processor.procesar(List.of(pendiente), List.of(cobro),
                LocalDate.of(2026, 9, 24), LocalDateTime.of(2026, 9, 24, 12, 0));

        assertThat(contribuyente.getNombre()).isEqualTo("Ana Pérez");
        assertThat(contribuyente.getDocumento()).isEqualTo("actual-123");
    }

    @Test
    void dtoDeCobroLeePersonaYDocumentoDelJson() throws Exception {
        GeoPagosFacturaCanceladaDto cobro = new ObjectMapper().readValue("""
                {"CM":123,"NUMERO_PADRON":4567,"PERSONA":"Ana Pérez","DOCUMENTO":"1234567-8"}
                """, GeoPagosFacturaCanceladaDto.class);

        assertThat(cobro.persona()).isEqualTo("Ana Pérez");
        assertThat(cobro.documento()).isEqualTo("1234567-8");
    }

    @Test
    void pendienteSinDocumentoConservaElAnteriorSiLaPersonaEsLaMisma() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Ana Pérez");
        contribuyente.setDocumento("1234567-8");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setNumeroPadron("4567");
        padron.setContribuyente(contribuyente);
        when(padronRepository.findAll()).thenReturn(List.of(padron));
        when(configuracionSegmentoService.reglasVigentes()).thenReturn(new ReglasSegmentacion(List.of()));
        GeoPagosFacturaPendienteDto pendiente = new GeoPagosFacturaPendienteDto(
                123, 4567, null, null, "COM", null, "Ana Pérez", null,
                null, null, null, null, "NO", null, new BigDecimal("100.00"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), "2026");

        processor.procesar(List.of(pendiente), List.of(),
                LocalDate.of(2026, 9, 24), LocalDateTime.of(2026, 9, 24, 12, 0));

        assertThat(contribuyente.getDocumento()).isEqualTo("1234567-8");
    }

    @Test
    void cambioDePersonaSinDocumentoLimpiaDocumentoYContactosDePersonas() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Ana Pérez");
        contribuyente.setDocumento("1234567-8");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setNumeroPadron("4567");
        padron.setContribuyente(contribuyente);
        when(padronRepository.findAll()).thenReturn(List.of(padron));
        when(configuracionSegmentoService.reglasVigentes()).thenReturn(new ReglasSegmentacion(List.of()));
        GeoPagosFacturaPendienteDto pendiente = new GeoPagosFacturaPendienteDto(
                123, 4567, null, null, "COM", null, "Luis Gómez", null,
                null, null, null, null, "NO", null, new BigDecimal("100.00"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), "2026");

        processor.procesar(List.of(pendiente), List.of(),
                LocalDate.of(2026, 9, 24), LocalDateTime.of(2026, 9, 24, 12, 0));

        assertThat(contribuyente.getNombre()).isEqualTo("Luis Gómez");
        assertThat(contribuyente.getDocumento()).isNull();
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<Dato>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(sincronizacionContactosService).sincronizar(captor.capture());
        assertThat(captor.getValue()).filteredOn(dato ->
                dato.origen().equals(SincronizacionContactosService.PERSONAS))
                .hasSize(2).allSatisfy(dato -> assertThat(dato.valor()).isNull());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0.00", "-10.00"})
    void noCreaContribuyentePadronNiDeudaSinImportePositivo(String importe) {
        var resultado = procesarImporte(importe == null ? null : new BigDecimal(importe));

        assertThat(resultado.deudasCreadas()).isZero();
        assertThat(resultado.registrosOmitidos()).isEqualTo(1);
        verify(contribuyenteRepository).saveAll(argThat(nuevos -> !nuevos.iterator().hasNext()));
        verify(padronRepository).saveAll(argThat(nuevos -> !nuevos.iterator().hasNext()));
        verify(deudaRepository).saveAll(argThat(nuevos -> !nuevos.iterator().hasNext()));
        verify(sincronizacionContactosService).sincronizar(List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-10.00"})
    void saldoNoPositivoCancelaLaDeudaExistenteConservandoSuIdentidad(String importe) {
        Deuda deuda = deudaExistente();
        when(deudaRepository.findAllConPadron()).thenReturn(List.of(deuda));

        var resultado = procesarImporte(new BigDecimal(importe));

        assertThat(resultado.deudasCanceladas()).isEqualTo(1);
        assertThat(resultado.deudasCreadas()).isZero();
        assertThat(deuda.getId()).isEqualTo(1L);
        assertThat(deuda.getEstado()).isEqualTo(EstadoDeuda.CANCELADA);
        assertThat(deuda.getImporte()).isEqualByComparingTo(importe);
        assertThat(deuda.getSegmentoMora()).isNull();
        assertThat(deuda.getFechaSincronizacion()).isNotNull();
    }

    @Test
    void importeDesconocidoNoCancelaNiReemplazaElSaldoDeUnaDeudaExistente() {
        Deuda deuda = deudaExistente();
        when(deudaRepository.findAllConPadron()).thenReturn(List.of(deuda));

        var resultado = procesarImporte(null);

        assertThat(resultado.deudasCanceladas()).isZero();
        assertThat(resultado.registrosOmitidos()).isEqualTo(1);
        assertThat(deuda.getEstado()).isEqualTo(EstadoDeuda.EN_GESTION);
        assertThat(deuda.getImporte()).isEqualByComparingTo("100.00");
        assertThat(deuda.getFechaSincronizacion()).isNull();
    }

    private com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion
            procesarImporte(BigDecimal importe) {
        GeoPagosFacturaPendienteDto fila = new GeoPagosFacturaPendienteDto(
                123, 4567, null, null, "COM", null, "Ana Pérez", null,
                null, null, null, null, "NO", null, importe,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), "2026");
        return processor.procesar(List.of(fila), List.of(),
                LocalDate.of(2026, 9, 24), LocalDateTime.of(2026, 9, 24, 12, 0));
    }

    private static Deuda deudaExistente() {
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setNumeroPadron("4567");
        Deuda deuda = new Deuda();
        deuda.setId(1L);
        deuda.setPadron(padron);
        deuda.setEstado(EstadoDeuda.EN_GESTION);
        deuda.setImporte(new BigDecimal("100.00"));
        return deuda;
    }
}
