package com.intendencia.gestion_morosidad_api.modules.contacto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.entity.Contacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.repository.ContactoRepository;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService.Dato;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SincronizacionContactosServiceTest {

    @Mock private ContactoRepository contactoRepository;
    private SincronizacionContactosService service;
    private Contribuyente contribuyente;

    @BeforeEach
    void preparar() {
        service = new SincronizacionContactosService(contactoRepository);
        contribuyente = new Contribuyente();
        contribuyente.setId(1L);
    }

    @Test
    void creaContactosDeCadaFuenteConSuPropietarioYOrigen() {
        when(contactoRepository.findByContribuyenteIn(List.of(contribuyente))).thenReturn(List.of());

        service.sincronizar(List.of(
                dato(TipoContacto.TELEFONO, SincronizacionContactosService.PERSONAS, " 099 123 456 ", true),
                dato(TipoContacto.EMAIL, SincronizacionContactosService.GEOPAGOS, "pago@example.test", false)));

        ArgumentCaptor<Iterable<Contacto>> captor = captor();
        verify(contactoRepository).saveAll(captor.capture());
        List<Contacto> guardados = toList(captor.getValue());
        assertThat(guardados).extracting(Contacto::getValor).containsExactly("099 123 456", "pago@example.test");
        assertThat(guardados).allSatisfy(c -> assertThat(c.getContribuyente()).isSameAs(contribuyente));
        assertThat(guardados.get(0).getEsDeContribuyente()).isTrue();
        assertThat(guardados.get(1).getEsDeContribuyente()).isFalse();
        assertThat(guardados).extracting(Contacto::getOrigen)
                .containsExactly(SincronizacionContactosService.PERSONAS, SincronizacionContactosService.GEOPAGOS);
    }

    @Test
    void sincronizacionRepetidaNoDuplicaNiModificaLosContactosManuales() {
        Contacto importado = contacto(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "ana@example.test", true);
        Contacto manual = contacto(TipoContacto.EMAIL, "MANUAL", "otro@example.test", true);
        when(contactoRepository.findByContribuyenteIn(List.of(contribuyente)))
                .thenReturn(List.of(importado, manual));

        service.sincronizar(List.of(dato(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "ana@example.test", true)));

        verify(contactoRepository).saveAll(List.of());
        verify(contactoRepository, never()).deleteAll(anyList());
        assertThat(manual.getValor()).isEqualTo("otro@example.test");
        assertThat(importado.getValor()).isEqualTo("ana@example.test");
    }

    @Test
    void conservaElContactoEditadoAunqueLaApiCambieSuValorOPropietario() {
        Contacto personas = contacto(TipoContacto.TELEFONO, SincronizacionContactosService.PERSONAS,
                "111", true);
        Contacto geopagos = contacto(TipoContacto.TELEFONO, SincronizacionContactosService.GEOPAGOS,
                "222", false);
        when(contactoRepository.findByContribuyenteIn(List.of(contribuyente)))
                .thenReturn(List.of(personas, geopagos));

        service.sincronizar(List.of(dato(TipoContacto.TELEFONO, SincronizacionContactosService.PERSONAS,
                "333", false)));

        verify(contactoRepository).saveAll(List.of());
        verify(contactoRepository, never()).deleteAll(anyList());
        assertThat(personas.getValor()).isEqualTo("111");
        assertThat(personas.getEsDeContribuyente()).isTrue();
        assertThat(geopagos.getValor()).isEqualTo("222");
    }

    @Test
    void valorVacioNoEliminaElContactoImportadoNiElManual() {
        Contacto importado = contacto(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "ana@example.test", true);
        Contacto manual = contacto(TipoContacto.EMAIL, "MANUAL", "otro@example.test", true);
        when(contactoRepository.findByContribuyenteIn(List.of(contribuyente)))
                .thenReturn(List.of(importado, manual));

        service.sincronizar(List.of(dato(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "  ", true)));

        verify(contactoRepository, never()).deleteAll(anyList());
        assertThat(importado.getValor()).isEqualTo("ana@example.test");
        verify(contactoRepository).saveAll(List.of());
        assertThat(manual.getValor()).isEqualTo("otro@example.test");
    }

    @Test
    void sinDatosNoConsultaNiModificaContactos() {
        service.sincronizar(List.of());
        verifyNoInteractions(contactoRepository);
    }

    @Test
    void dosPadronesDelMismoContribuyenteNoCreanElMismoContactoDosVeces() {
        when(contactoRepository.findByContribuyenteIn(List.of(contribuyente))).thenReturn(List.of());

        service.sincronizar(List.of(
                dato(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS, "viejo@example.test", true),
                dato(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS, "nuevo@example.test", true)));

        ArgumentCaptor<Iterable<Contacto>> captor = captor();
        verify(contactoRepository).saveAll(captor.capture());
        assertThat(toList(captor.getValue())).singleElement()
                .extracting(Contacto::getValor).isEqualTo("nuevo@example.test");
    }

    @Test
    void conservaTodosLosContactosExistentesInclusoSiCompartenTipoYFuente() {
        Contacto primero = contacto(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "ana@example.test", true);
        Contacto duplicado = contacto(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "ana@example.test", true);
        when(contactoRepository.findByContribuyenteIn(List.of(contribuyente)))
                .thenReturn(List.of(primero, duplicado));

        service.sincronizar(List.of(dato(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "ana@example.test", true)));

        verify(contactoRepository, never()).deleteAll(anyList());
        assertThat(primero.getValor()).isEqualTo("ana@example.test");
        assertThat(duplicado.getValor()).isEqualTo("ana@example.test");
        verify(contactoRepository).saveAll(List.of());
    }

    static Stream<Arguments> fuentesYValores() {
        return Stream.of(SincronizacionContactosService.PERSONAS,
                        SincronizacionContactosService.GEOPAGOS,
                        SincronizacionContactosService.PENDIENTES)
                .flatMap(origen -> Stream.of(TipoContacto.values())
                        .flatMap(tipo -> Stream.of("nuevo", "", "  ", null)
                                .map(valor -> Arguments.of(origen, tipo, valor))));
    }

    @ParameterizedTest
    @MethodSource("fuentesYValores")
    void conservaValorYPropietarioExistentesParaTodasLasFuentes(
            String origen, TipoContacto tipo, String valorApi) {
        Contacto existente = contacto(tipo, origen, "corregido manualmente", true);
        when(contactoRepository.findByContribuyenteIn(List.of(contribuyente)))
                .thenReturn(List.of(existente));

        service.sincronizar(List.of(dato(tipo, origen, valorApi, false)));

        assertThat(existente.getValor()).isEqualTo("corregido manualmente");
        assertThat(existente.getEsDeContribuyente()).isTrue();
        verify(contactoRepository).saveAll(List.of());
        verify(contactoRepository, never()).deleteAll(anyList());
    }

    @Test
    void agregaUnTipoFaltanteSinModificarElContactoExistente() {
        Contacto telefono = contacto(TipoContacto.TELEFONO,
                SincronizacionContactosService.PERSONAS, "099111111", true);
        when(contactoRepository.findByContribuyenteIn(List.of(contribuyente)))
                .thenReturn(List.of(telefono));

        service.sincronizar(List.of(
                dato(TipoContacto.TELEFONO, SincronizacionContactosService.PERSONAS, "099222222", false),
                dato(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS, "nuevo@example.test", true),
                dato(TipoContacto.DOMICILIO, SincronizacionContactosService.PERSONAS, null, true)));

        ArgumentCaptor<Iterable<Contacto>> captor = captor();
        verify(contactoRepository).saveAll(captor.capture());
        assertThat(toList(captor.getValue())).singleElement()
                .extracting(Contacto::getValor).isEqualTo("nuevo@example.test");
        assertThat(telefono.getValor()).isEqualTo("099111111");
        verify(contactoRepository, never()).deleteAll(anyList());
    }

    private Dato dato(TipoContacto tipo, String origen, String valor, boolean propio) {
        return new Dato(contribuyente, tipo, origen, valor, propio);
    }

    private Contacto contacto(TipoContacto tipo, String origen, String valor, boolean propio) {
        Contacto contacto = new Contacto();
        contacto.setContribuyente(contribuyente);
        contacto.setTipoContacto(tipo);
        contacto.setOrigen(origen);
        contacto.setValor(valor);
        contacto.setEsDeContribuyente(propio);
        return contacto;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Iterable<Contacto>> captor() {
        return ArgumentCaptor.forClass((Class) Iterable.class);
    }

    private static List<Contacto> toList(Iterable<Contacto> contactos) {
        List<Contacto> lista = new ArrayList<>();
        contactos.forEach(lista::add);
        return lista;
    }
}
