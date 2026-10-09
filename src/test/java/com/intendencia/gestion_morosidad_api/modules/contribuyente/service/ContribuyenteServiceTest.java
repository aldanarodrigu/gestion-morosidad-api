package com.intendencia.gestion_morosidad_api.modules.contribuyente.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

/**
 * Las consultas solo leen la base: la carga desde GeoPagos se prueba en SincronizacionDeudasProcessorTest.
 * Los filtros por nombre y documento se resuelven en la base (PadronSpecifications).
 */
@ExtendWith(MockitoExtension.class)
class ContribuyenteServiceTest {

    @Mock private PadronRepository padronRepository;
    @InjectMocks private ContribuyenteService service;

    @Test
    void busquedaPorCmUsaElCmDelPadronEnLaRespuesta() {
        when(padronRepository.findByCm("123")).thenReturn(Optional.of(padron("123", "4567", "Ana Pérez", null)));

        var respuesta = service.buscarPorCm("123").orElseThrow();

        assertThat(respuesta.cm()).isEqualTo("123");
        assertThat(respuesta.nombre()).isEqualTo("Ana Pérez");
    }

    @Test
    void busquedaPorCmInexistenteNoConsultaNadaMas() {
        when(padronRepository.findByCm("999")).thenReturn(Optional.empty());

        assertThat(service.buscarPorCm("999")).isEmpty();
        assertThat(service.listarPadronesPorCm("999")).isEmpty();
        verify(padronRepository, never()).findByContribuyenteOrderByNumeroPadronAsc(any());
    }

    @Test
    void listadoDevuelveUnaPaginaConElCmDeCadaPadron() {
        var pageable = PageRequest.of(0, 20);
        when(padronRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(
                List.of(padron("123", "4567", "Ana Pérez", "1234567-8"), padron("124", "4568", "Luis Gómez", null)),
                pageable, 42));

        var respuesta = service.listarContribuyentes("ana", null, pageable);

        assertThat(respuesta.contenido()).extracting(r -> r.cm()).containsExactly("123", "124");
        assertThat(respuesta.totalElementos()).isEqualTo(42);
        assertThat(respuesta.totalPaginas()).isEqualTo(3);
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
}
