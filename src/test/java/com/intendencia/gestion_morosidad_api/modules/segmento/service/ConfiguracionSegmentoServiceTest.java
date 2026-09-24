package com.intendencia.gestion_morosidad_api.modules.segmento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.intendencia.gestion_morosidad_api.modules.segmento.dto.ConfiguracionSegmentoRequest;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.ConfiguracionSegmentoResponse;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.ConfiguracionSegmento;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.ConfiguracionSegmentoRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.SegmentoMoraRepository;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ConfiguracionSegmentoServiceTest {

    @Mock
    private ConfiguracionSegmentoRepository configuracionRepository;
    @Mock
    private SegmentoMoraRepository segmentoMoraRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private ConfiguracionSegmentoService service;

    private SegmentoMora temprana;
    private ConfiguracionSegmento rangoTemprana;
    private ConfiguracionSegmento rangoInicial;

    @BeforeEach
    void setUp() {
        temprana = segmento(1L, "Mora Temprana");
        rangoTemprana = rango(temprana, 0, 90);
        rangoInicial = rango(segmento(2L, "Mora Inicial"), 91, 365);
    }

    @Test
    void reemplazaElRangoDelMismoSegmentoYPublicaElEvento() {
        when(segmentoMoraRepository.findById(1L)).thenReturn(Optional.of(temprana));
        when(configuracionRepository.findByActivaTrueOrderByDiasDesdeAsc()).thenReturn(List.of(rangoTemprana, rangoInicial));
        when(configuracionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConfiguracionSegmentoResponse respuesta = service.crear(new ConfiguracionSegmentoRequest(1L, 0, 60));

        assertThat(respuesta.diasHasta()).isEqualTo(60);
        assertThat(rangoTemprana.isActiva()).isFalse();
        assertThat(rangoInicial.isActiva()).isTrue();
        verify(configuracionRepository).saveAndFlush(rangoTemprana);
        verify(eventPublisher).publishEvent(any(ConfiguracionSegmentoCambiadaEvent.class));
    }

    @Test
    void rechazaUnRangoQueSeSuperponeConOtroSegmento() {
        when(segmentoMoraRepository.findById(1L)).thenReturn(Optional.of(temprana));
        when(configuracionRepository.findByActivaTrueOrderByDiasDesdeAsc()).thenReturn(List.of(rangoTemprana, rangoInicial));

        assertThatThrownBy(() -> service.crear(new ConfiguracionSegmentoRequest(1L, 0, 120)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Mora Inicial");
        assertThat(rangoTemprana.isActiva()).isTrue();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rechazaUnRangoSinTopeQueSeSuperponeConOtroSegmento() {
        when(segmentoMoraRepository.findById(1L)).thenReturn(Optional.of(temprana));
        when(configuracionRepository.findByActivaTrueOrderByDiasDesdeAsc()).thenReturn(List.of(rangoTemprana, rangoInicial));

        assertThatThrownBy(() -> service.crear(new ConfiguracionSegmentoRequest(1L, 0, null)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void rechazaDiasHastaMenorQueDiasDesde() {
        when(segmentoMoraRepository.findById(1L)).thenReturn(Optional.of(temprana));

        assertThatThrownBy(() -> service.crear(new ConfiguracionSegmentoRequest(1L, 50, 10)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void segmentoInexistenteLanzaNoEncontrado() {
        when(segmentoMoraRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(new ConfiguracionSegmentoRequest(9L, 0, 10)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private static SegmentoMora segmento(Long id, String nombre) {
        SegmentoMora segmento = new SegmentoMora();
        segmento.setId(id);
        segmento.setNombre(nombre);
        return segmento;
    }

    private static ConfiguracionSegmento rango(SegmentoMora segmento, int desde, Integer hasta) {
        ConfiguracionSegmento configuracion = new ConfiguracionSegmento();
        configuracion.setSegmento(segmento);
        configuracion.setDiasDesde(desde);
        configuracion.setDiasHasta(hasta);
        return configuracion;
    }
}
