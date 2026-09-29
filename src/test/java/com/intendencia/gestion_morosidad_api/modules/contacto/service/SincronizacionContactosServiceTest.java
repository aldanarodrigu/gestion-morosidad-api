package com.intendencia.gestion_morosidad_api.modules.contacto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.entity.Contacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.repository.ContactoRepository;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService.Dato;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        verify(contactoRepository).deleteAll(List.of());
        assertThat(manual.getValor()).isEqualTo("otro@example.test");
        assertThat(importado.getValor()).isEqualTo("ana@example.test");
    }

    @Test
    void actualizaSoloLaFuenteQueCambio() {
        Contacto personas = contacto(TipoContacto.TELEFONO, SincronizacionContactosService.PERSONAS,
                "111", true);
        Contacto geopagos = contacto(TipoContacto.TELEFONO, SincronizacionContactosService.GEOPAGOS,
                "222", false);
        when(contactoRepository.findByContribuyenteIn(List.of(contribuyente)))
                .thenReturn(List.of(personas, geopagos));

        service.sincronizar(List.of(dato(TipoContacto.TELEFONO, SincronizacionContactosService.PERSONAS,
                "333", true)));

        verify(contactoRepository).saveAll(List.of(personas));
        assertThat(personas.getValor()).isEqualTo("333");
        assertThat(geopagos.getValor()).isEqualTo("222");
    }

    @Test
    void valorVacioEliminaSoloElContactoImportadoDeEsaFuente() {
        Contacto importado = contacto(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "ana@example.test", true);
        Contacto manual = contacto(TipoContacto.EMAIL, "MANUAL", "otro@example.test", true);
        when(contactoRepository.findByContribuyenteIn(List.of(contribuyente)))
                .thenReturn(List.of(importado, manual));

        service.sincronizar(List.of(dato(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "  ", true)));

        verify(contactoRepository).deleteAll(List.of(importado));
        verify(contactoRepository).saveAll(List.of());
        assertThat(manual.getValor()).isEqualTo("otro@example.test");
    }

    @Test
    void sinDatosNoConsultaNiModificaContactos() {
        service.sincronizar(List.of());
        verify(contactoRepository, never()).findByContribuyenteIn(anyList());
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
    void eliminaDuplicadosImportadosQueYaEstabanGuardados() {
        Contacto primero = contacto(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "ana@example.test", true);
        Contacto duplicado = contacto(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "ana@example.test", true);
        when(contactoRepository.findByContribuyenteIn(List.of(contribuyente)))
                .thenReturn(List.of(primero, duplicado));

        service.sincronizar(List.of(dato(TipoContacto.EMAIL, SincronizacionContactosService.PERSONAS,
                "ana@example.test", true)));

        verify(contactoRepository).deleteAll(List.of(duplicado));
        verify(contactoRepository).saveAll(List.of());
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
