package com.intendencia.gestion_morosidad_api.modules.padron.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import java.util.Optional;
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
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Ana Pérez");
        contribuyente.setDocumento("1234567-8");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setNumeroPadron("4567");
        padron.setContribuyente(contribuyente);
        when(padronRepository.findByNumeroPadron("4567")).thenReturn(Optional.of(padron));

        var respuesta = service.buscarPorNumeroPadron("4567").orElseThrow();

        assertThat(respuesta.cm()).isEqualTo("123");
        assertThat(respuesta.contribuyente().cm()).isEqualTo("123");
        assertThat(respuesta.contribuyente().nombre()).isEqualTo("Ana Pérez");
        assertThat(respuesta.contribuyente().documento()).isEqualTo("1234567-8");
    }
}
