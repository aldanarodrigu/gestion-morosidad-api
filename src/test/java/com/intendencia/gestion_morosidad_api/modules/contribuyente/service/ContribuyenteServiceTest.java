package com.intendencia.gestion_morosidad_api.modules.contribuyente.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService.Dato;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContribuyenteServiceTest {

    @Mock private GeoPagosClient geoPagosClient;
    @Mock private ContribuyenteRepository contribuyenteRepository;
    @Mock private PadronRepository padronRepository;
    @Mock private SincronizacionContactosService sincronizacionContactosService;
    @InjectMocks private ContribuyenteService service;

    @Test
    void sincronizacionVinculaPadronYContribuyentePorCm() {
        GeoPagosContribuyenteDto fila = new GeoPagosContribuyenteDto(
                123, 4567, "COM", "San José", null, null, "Ana Pérez",
                null, null, null, null, null, null);
        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(fila), null));
        when(padronRepository.findByCm("123")).thenReturn(Optional.empty());
        when(contribuyenteRepository.save(any(Contribuyente.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.sincronizarDesdeGeoPagos();

        ArgumentCaptor<Padron> padronCaptor = ArgumentCaptor.forClass(Padron.class);
        verify(padronRepository).save(padronCaptor.capture());
        Padron padron = padronCaptor.getValue();
        assertThat(padron.getCm()).isEqualTo("123");
        assertThat(padron.getNumeroPadron()).isEqualTo("4567");
        assertThat(padron.getContribuyente().getNombre()).isEqualTo("Ana Pérez");
        verify(contribuyenteRepository).save(padron.getContribuyente());
    }

    @Test
    void sincronizacionReutilizaElContribuyenteDelPadronExistente() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Nombre anterior");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setNumeroPadron("4567");
        padron.setContribuyente(contribuyente);
        GeoPagosContribuyenteDto fila = new GeoPagosContribuyenteDto(
                123, 4567, "COM", "San José", null, null, "Ana Pérez",
                null, null, null, null, null, null);
        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(fila), null));
        when(padronRepository.findByCm("123")).thenReturn(Optional.of(padron));
        when(contribuyenteRepository.save(same(contribuyente))).thenReturn(contribuyente);

        service.sincronizarDesdeGeoPagos();

        verify(contribuyenteRepository).save(same(contribuyente));
        verify(padronRepository).save(same(padron));
        assertThat(padron.getContribuyente()).isSameAs(contribuyente);
        assertThat(contribuyente.getNombre()).isEqualTo("Ana Pérez");
    }

    @Test
    void busquedaPorCmUsaElCmDelPadronEnLaRespuesta() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Ana Pérez");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setContribuyente(contribuyente);
        when(padronRepository.findByCm("123")).thenReturn(Optional.of(padron));

        var respuesta = service.buscarPorCm("123").orElseThrow();

        assertThat(respuesta.cm()).isEqualTo("123");
        assertThat(respuesta.nombre()).isEqualTo("Ana Pérez");
    }

    @Test
    void listadoTomaElCmDeCadaPadron() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Ana Pérez");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setContribuyente(contribuyente);
        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(padronRepository.findByCmIn(any())).thenReturn(List.of(padron));

        var respuesta = service.listarContribuyentes(null, null);

        assertThat(respuesta).hasSize(1);
        assertThat(respuesta.getFirst().cm()).isEqualTo("123");
        assertThat(respuesta.getFirst().nombre()).isEqualTo("Ana Pérez");
    }

    @Test
    void listadoSinFiltrosIncluyeDeudoresInclusoSinDocumento() {
        Padron ana = padron("123", "4567", "Ana Pérez", "1234567-8");
        Padron luis = padron("124", "4568", "Luis Gómez", null);
        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(padronRepository.findByCmIn(any())).thenReturn(List.of(ana, luis));

        var respuesta = service.listarContribuyentes("  ", null);

        assertThat(respuesta).extracting(r -> r.cm()).containsExactly("123", "124");
    }

    @Test
    void filtraPorNombreParcialSinDistinguirMayusculas() {
        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(padronRepository.findByCmIn(any())).thenReturn(List.of(
                padron("123", "4567", "Ana Pérez", "1234567-8"),
                padron("124", "4568", "Luis Gómez", "8765432-1")));

        var respuesta = service.listarContribuyentes("  pÉReZ  ", null);

        assertThat(respuesta).extracting(r -> r.cm()).containsExactly("123");
    }

    @Test
    void filtraPorDocumentoParcialYExcluyeValoresNulos() {
        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(padronRepository.findByCmIn(any())).thenReturn(List.of(
                padron("123", "4567", "Ana Pérez", "AB-123"),
                padron("124", "4568", "Luis Gómez", null),
                padron("125", "4569", "Eva Díaz", "CD-456")));

        var respuesta = service.listarContribuyentes(null, " b-12 ");

        assertThat(respuesta).extracting(r -> r.cm()).containsExactly("123");
    }

    @Test
    void ambosFiltrosSeAplicanAlMismoContribuyente() {
        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(padronRepository.findByCmIn(any())).thenReturn(List.of(
                padron("123", "4567", "Ana Pérez", "ABC-123"),
                padron("124", "4568", "Ana López", "XYZ-999"),
                padron("125", "4569", "Luis Gómez", "ABC-123")));

        var respuesta = service.listarContribuyentes("ana", "abc");

        assertThat(respuesta).extracting(r -> r.cm()).containsExactly("123");
    }

    @Test
    void listaTodosLosPadronesVinculadosAlContribuyenteDelCm() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Ana Pérez");
        Padron primero = new Padron();
        primero.setCm("123");
        primero.setNumeroPadron("4567");
        primero.setContribuyente(contribuyente);
        Padron segundo = new Padron();
        segundo.setCm("124");
        segundo.setNumeroPadron("4568");
        segundo.setContribuyente(contribuyente);
        when(padronRepository.findByCm("124")).thenReturn(Optional.of(segundo));
        when(padronRepository.findByContribuyenteOrderByNumeroPadronAsc(contribuyente))
                .thenReturn(List.of(primero, segundo));

        var respuesta = service.listarPadronesPorCm("124").orElseThrow();

        assertThat(respuesta).extracting(r -> r.numeroPadron()).containsExactly("4567", "4568");
        assertThat(respuesta).extracting(r -> r.cm()).containsExactly("123", "124");
        assertThat(respuesta).allSatisfy(r -> assertThat(r.contribuyente().nombre()).isEqualTo("Ana Pérez"));
    }

    @Test
    void padronesPorCmInexistenteDevuelveAusenciaTrasIntentarSincronizar() {
        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));

        assertThat(service.listarPadronesPorCm("999")).isEmpty();

        verify(padronRepository, never()).findByContribuyenteOrderByNumeroPadronAsc(any());
        verify(geoPagosClient).obtenerContribuyentes();
    }

    @Test
    void padronesPorCmDesconocidoSeReintentaDespuesDeSincronizar() {
        Padron padron = padron("123", "4567", "Ana Pérez", null);
        when(padronRepository.findByCm("123"))
                .thenReturn(Optional.empty(), Optional.of(padron));
        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(padronRepository.findByContribuyenteOrderByNumeroPadronAsc(padron.getContribuyente()))
                .thenReturn(List.of(padron));

        assertThat(service.listarPadronesPorCm("123").orElseThrow())
                .extracting(r -> r.numeroPadron()).containsExactly("4567");
    }

    private static Padron padron(String cm, String numeroPadron, String nombre, String documento) {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre(nombre);
        contribuyente.setDocumento(documento);
        Padron padron = new Padron();
        padron.setCm(cm);
        padron.setNumeroPadron(numeroPadron);
        padron.setContribuyente(contribuyente);
        return padron;
    }

    @Test
    void sincronizacionImportaContactosDePersonasYDelUltimoGeoPago() {
        GeoPagosContribuyenteDto fila = new GeoPagosContribuyenteDto(
                123, 4567, "COM", "San José", null, null, "Ana Pérez",
                "099123456", "ana@example.test", "Otro pagador", "098765432",
                "pago@example.test", "2026-09-02 14:30:00");
        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(fila), null));
        when(padronRepository.findByCm("123")).thenReturn(Optional.empty());
        when(contribuyenteRepository.save(any(Contribuyente.class)))
                .thenAnswer(invocation -> {
                    Contribuyente c = invocation.getArgument(0);
                    c.setId(1L);
                    return c;
                });

        service.sincronizarDesdeGeoPagos();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<Dato>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(sincronizacionContactosService).sincronizar(captor.capture());
        List<Dato> contactos = captor.getValue();
        assertThat(contactos).hasSize(4);
        assertThat(contactos).extracting(Dato::tipo)
                .containsExactly(TipoContacto.TELEFONO, TipoContacto.EMAIL,
                        TipoContacto.TELEFONO, TipoContacto.EMAIL);
        assertThat(contactos).extracting(Dato::origen)
                .containsExactly(SincronizacionContactosService.PERSONAS,
                        SincronizacionContactosService.PERSONAS,
                        SincronizacionContactosService.GEOPAGOS,
                        SincronizacionContactosService.GEOPAGOS);
        assertThat(contactos).extracting(Dato::valor)
                .containsExactly("099123456", "ana@example.test", "098765432", "pago@example.test");
        assertThat(contactos).extracting(Dato::esDeContribuyente)
                .containsExactly(true, true, false, false);
        assertThat(contactos).allSatisfy(dato -> assertThat(dato.contribuyente().getId()).isEqualTo(1L));
    }

    @Test
    void sincronizacionMantieneDocumentoSiEsLaMismaPersonaYLoLimpiaSiCambia() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Ana Pérez");
        contribuyente.setDocumento("1234567-8");
        Padron padron = new Padron();
        padron.setContribuyente(contribuyente);
        when(padronRepository.findByCm("123")).thenReturn(Optional.of(padron));
        when(contribuyenteRepository.save(same(contribuyente))).thenReturn(contribuyente);
        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes()).thenReturn(new IntendenciaApiResponse<>(List.of(
                new GeoPagosContribuyenteDto(123, 4567, "COM", null, null, null,
                        "Ana Pérez", null, null, null, null, null, null)), null));

        service.sincronizarDesdeGeoPagos();
        assertThat(contribuyente.getDocumento()).isEqualTo("1234567-8");

        pendientes("123", "124", "125");
        when(geoPagosClient.obtenerContribuyentes()).thenReturn(new IntendenciaApiResponse<>(List.of(
                new GeoPagosContribuyenteDto(123, 4567, "COM", null, null, null,
                        "Luis Gómez", null, null, null, null, null, null)), null));
        service.sincronizarDesdeGeoPagos();
        assertThat(contribuyente.getNombre()).isEqualTo("Luis Gómez");
        assertThat(contribuyente.getDocumento()).isNull();
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<Dato>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(sincronizacionContactosService, org.mockito.Mockito.times(2)).sincronizar(captor.capture());
        assertThat(captor.getAllValues().getLast()).filteredOn(dato ->
                dato.origen().equals(SincronizacionContactosService.PENDIENTES))
                .hasSize(3).allSatisfy(dato -> assertThat(dato.valor()).isNull());
    }
    @Test
    void importaSoloLosCmConDeudaPositivaYNormalizaIdentificadores() {
        when(geoPagosClient.obtenerFacturasPendientes()).thenReturn(new IntendenciaApiResponse<>(List.of(
                pendiente(new BigDecimal("123.00"), new BigDecimal("100.00")),
                pendiente(124, BigDecimal.ZERO),
                pendiente(125, new BigDecimal("-10.00")),
                pendiente(126, null)), null));
        when(geoPagosClient.obtenerContribuyentes()).thenReturn(new IntendenciaApiResponse<>(List.of(
                contribuyente(" 123 "), contribuyente(124), contribuyente(125),
                contribuyente(126), contribuyente(127)), null));
        when(contribuyenteRepository.save(any(Contribuyente.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.sincronizarDesdeGeoPagos();

        ArgumentCaptor<Padron> captor = ArgumentCaptor.forClass(Padron.class);
        verify(padronRepository).save(captor.capture());
        assertThat(captor.getValue().getCm()).isEqualTo("123");
        verify(padronRepository, never()).findByCm("124");
        verify(padronRepository, never()).findByCm("125");
        verify(padronRepository, never()).findByCm("126");
        verify(padronRepository, never()).findByCm("127");
    }

    @Test
    void listadoConsultaSoloLosCmConDeudaAunqueYaExistanOtrosEnLaBase() {
        pendientes("123");
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(padronRepository.findByCmIn(Set.of("123")))
                .thenReturn(List.of(padron("123", "4567", "Ana Pérez", null)));

        assertThat(service.listarContribuyentes(null, null)).extracting(r -> r.cm())
                .containsExactly("123");
        verify(padronRepository).findByCmIn(Set.of("123"));
        verify(padronRepository, never()).findAll();
    }

    @Test
    void sinDeudaPositivaNoConsultaNiImportaElListadoCompletoDeContribuyentes() {
        when(geoPagosClient.obtenerFacturasPendientes()).thenReturn(new IntendenciaApiResponse<>(List.of(
                pendiente(123, BigDecimal.ZERO), pendiente(124, new BigDecimal("-1")),
                pendiente(125, null)), null));

        assertThat(service.listarContribuyentes(null, null)).isEmpty();

        verify(geoPagosClient, never()).obtenerContribuyentes();
        verifyNoInteractions(padronRepository, contribuyenteRepository, sincronizacionContactosService);
    }

    @Test
    void respuestaDePendientesSinDatosFallaSinImportarContribuyentes() {
        when(geoPagosClient.obtenerFacturasPendientes())
                .thenReturn(new IntendenciaApiResponse<>(null, null));

        assertThatThrownBy(service::sincronizarDesdeGeoPagos)
                .isInstanceOf(IntegracionExternaException.class);

        verify(geoPagosClient, never()).obtenerContribuyentes();
        verifyNoInteractions(padronRepository, contribuyenteRepository, sincronizacionContactosService);
    }

    private void pendientes(String... cms) {
        when(geoPagosClient.obtenerFacturasPendientes()).thenReturn(new IntendenciaApiResponse<>(
                Arrays.stream(cms).map(cm -> pendiente(cm, new BigDecimal("100.00"))).toList(), null));
    }

    private static GeoPagosFacturaPendienteDto pendiente(Object cm, BigDecimal importe) {
        return new GeoPagosFacturaPendienteDto(cm, 4567, null, null, "COM", null,
                "Ana Pérez", null, null, null, null, null, "NO", null, importe,
                null, null, null);
    }

    private static GeoPagosContribuyenteDto contribuyente(Object cm) {
        return new GeoPagosContribuyenteDto(cm, 4567, "COM", null, null, null,
                "Ana Pérez", null, null, null, null, null, null);
    }

}
