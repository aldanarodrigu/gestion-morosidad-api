package com.intendencia.gestion_morosidad_api.modules.padron.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PadronServiceTest {

    @Mock private PadronRepository padronRepository;
    @Mock private ContribuyenteService contribuyenteService;
    @InjectMocks private PadronService service;

    @Test
    void padronIncluyeSuContribuyente() {
        Padron padron = padron("123", "4567", "Libertad", "Ana Pérez", "1234567-8");
        when(padronRepository.findByNumeroPadronOrderByLocalidadAscCmAsc("4567")).thenReturn(List.of(padron));

        var respuesta = service.buscarPorNumeroPadron("4567", null).getFirst();

        assertThat(respuesta.cm()).isEqualTo("123");
        assertThat(respuesta.contribuyente().cm()).isEqualTo("123");
        assertThat(respuesta.contribuyente().nombre()).isEqualTo("Ana Pérez");
        assertThat(respuesta.contribuyente().documento()).isEqualTo("1234567-8");
    }

    @Test
    void mismoNumeroEnDistintasLocalidadesDevuelveTodos() {
        when(padronRepository.findByNumeroPadronOrderByLocalidadAscCmAsc("4567")).thenReturn(List.of(
                padron("123", "4567", "Libertad", "Ana Pérez", null),
                padron("890", "4567", "San José de Mayo", "Juan Gómez", null)));

        assertThat(service.buscarPorNumeroPadron("4567", null))
                .extracting(PadronResponse::cm)
                .containsExactly("123", "890");
    }

    @Test
    void filtraPorLocalidadSinDistinguirMayusculas() {
        when(padronRepository.findByNumeroPadronOrderByLocalidadAscCmAsc("4567")).thenReturn(List.of(
                padron("123", "4567", "Libertad", "Ana Pérez", null),
                padron("890", "4567", "San José de Mayo", "Juan Gómez", null)));

        assertThat(service.buscarContribuyentes("4567", "  libertad "))
                .singleElement()
                .satisfies(c -> assertThat(c.nombre()).isEqualTo("Ana Pérez"));
    }

    private static Padron padron(String cm, String numero, String localidad, String nombre, String documento) {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre(nombre);
        contribuyente.setDocumento(documento);
        Padron padron = new Padron();
        padron.setCm(cm);
        padron.setNumeroPadron(numero);
        padron.setLocalidad(localidad);
        padron.setContribuyente(contribuyente);
        return padron;
    }
}
