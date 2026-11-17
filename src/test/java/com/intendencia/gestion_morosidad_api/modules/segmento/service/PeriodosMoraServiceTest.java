package com.intendencia.gestion_morosidad_api.modules.segmento.service;

import com.intendencia.gestion_morosidad_api.modules.segmento.dto.*;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.*;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.*;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PeriodosMoraServiceTest {
    @Mock ConfiguracionSegmentoRepository configuraciones;
    @Mock SegmentoMoraRepository segmentos;
    @Mock PadronRepository padrones;
    @Mock ApplicationEventPublisher eventos;
    @InjectMocks ConfiguracionSegmentoService servicio;
    List<SegmentoMora> catalogo;
    List<ConfiguracionSegmento> base, propias;

    @BeforeEach void preparar() {
        catalogo = new ArrayList<>();
        for (int i=1;i<=3;i++) {
            var s=new SegmentoMora();s.setId((long)i);s.setOrden(i);s.setNombre("Segmento "+i);catalogo.add(s);
        }
        base=rangos(null);propias=rangos("CIU");
        lenient().when(segmentos.findAllByOrderByOrdenAsc()).thenReturn(catalogo);
        lenient().when(padrones.listarTiposPadron()).thenReturn(List.of("CIU","CIR"));
        lenient().when(configuraciones.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of());
        lenient().when(configuraciones.findByActivaTrueOrderByDiasDesdeAsc()).thenReturn(base);
    }
    List<ConfiguracionSegmento> rangos(String tipo) {
        var r=new ArrayList<ConfiguracionSegmento>();
        for(int i=0;i<3;i++) {
            var c=new ConfiguracionSegmento();c.setSegmento(catalogo.get(i));c.setTipoPadron(tipo);
            c.setDiasDesde(new int[]{0,91,366}[i]);c.setDiasHasta(i==2?null:i==0?90:365);r.add(c);
        }
        return r;
    }
    List<ConfiguracionSegmentoRequest> solicitudes() {
        return new ArrayList<>(List.of(new ConfiguracionSegmentoRequest(1L,0,120),
                new ConfiguracionSegmentoRequest(2L,121,450),new ConfiguracionSegmentoRequest(3L,451,null)));
    }
    @Test void reemplazaSoloElPadTipoYConservaHistorialSinTocarLaBase() {
        var activas=new ArrayList<>(base);activas.addAll(propias);
        when(configuraciones.findByActivaTrueOrderByDiasDesdeAsc()).thenReturn(activas);
        when(configuraciones.saveAllAndFlush(anyList())).thenAnswer(i->i.getArgument(0));
        var solicitudes=solicitudes();Collections.reverse(solicitudes);
        var respuesta=servicio.actualizarPeriodos(new PeriodosMoraRequest(" CIU ",solicitudes,null));
        assertThat(propias).allMatch(c->!c.isActiva());assertThat(base).allMatch(ConfiguracionSegmento::isActiva);
        assertThat(respuesta).extracting(ConfiguracionSegmentoResponse::diasDesde).containsExactly(0,121,451);
        assertThat(respuesta).allMatch(c->c.activa() && c.tipoPadron().equals("CIU"));
        var orden=inOrder(configuraciones,eventos);
        orden.verify(configuraciones).saveAll(propias);orden.verify(configuraciones).flush();
        orden.verify(configuraciones).saveAllAndFlush(anyList());
        orden.verify(eventos).publishEvent(new ConfiguracionSegmentoCambiadaEvent("CIU"));
    }
    @ParameterizedTest @ValueSource(strings={"hueco","solapamiento","negativo","invertido","inicio","sinTopeInicial","topeFinal","duplicado","faltante","desconocido"})
    void rechazaRangosInvalidosAntesDeEscribirORecalcular(String caso) {
        var r=solicitudes();
        switch(caso) {
            case "hueco" -> r.set(1,new ConfiguracionSegmentoRequest(2L,122,450));
            case "solapamiento" -> r.set(1,new ConfiguracionSegmentoRequest(2L,120,450));
            case "negativo" -> r.set(0,new ConfiguracionSegmentoRequest(1L,-1,120));
            case "invertido" -> r.set(1,new ConfiguracionSegmentoRequest(2L,121,100));
            case "inicio" -> r.set(0,new ConfiguracionSegmentoRequest(1L,1,120));
            case "sinTopeInicial" -> r.set(0,new ConfiguracionSegmentoRequest(1L,0,null));
            case "topeFinal" -> r.set(2,new ConfiguracionSegmentoRequest(3L,451,999));
            case "duplicado" -> r.set(1,new ConfiguracionSegmentoRequest(1L,121,450));
            case "faltante" -> r.remove(2);
            case "desconocido" -> r.set(2,new ConfiguracionSegmentoRequest(999L,451,null));
        }
        assertThatThrownBy(()->servicio.actualizarPeriodos(new PeriodosMoraRequest(null,r,null)))
                .isInstanceOf(caso.equals("desconocido")?RecursoNoEncontradoException.class:ReglaNegocioException.class);
        verify(configuraciones,never()).saveAll(any());verify(configuraciones,never()).flush();
        verify(configuraciones,never()).saveAllAndFlush(any());verifyNoInteractions(eventos);
        assertThat(base).allMatch(ConfiguracionSegmento::isActiva);
    }
    @Test void tipoSinConfiguracionHeredaLaBasePeroSuHistorialNoIncluyeLaBase() {
        assertThat(servicio.listar(false,"CIR")).extracting(ConfiguracionSegmentoResponse::diasDesde).containsExactly(0,91,366);
        assertThat(servicio.listar(true,"CIR")).isEmpty();
    }
    @Test void restaurarDesactivaSoloSusRangosYNotificaElRecalculo() {
        var activas=new ArrayList<>(base);activas.addAll(propias);
        when(configuraciones.findByActivaTrueOrderByDiasDesdeAsc()).thenReturn(activas);
        servicio.restaurarBase(" CIU ");
        assertThat(propias).allMatch(c->!c.isActiva());assertThat(base).allMatch(ConfiguracionSegmento::isActiva);
        verify(configuraciones).saveAllAndFlush(propias);verify(eventos).publishEvent(new ConfiguracionSegmentoCambiadaEvent("CIU"));
    }
    @Test void restaurarUnTipoQueYaHeredaNoGeneraHistorialNiRecalculo() {
        servicio.restaurarBase("CIR");verify(configuraciones,never()).saveAllAndFlush(any());verifyNoInteractions(eventos);
    }
    @ParameterizedTest @ValueSource(strings={"","NO_EXISTE"})
    void padTipoInvalidoNoModificaRangos(String tipo) {
        assertThatThrownBy(()->servicio.actualizarPeriodos(new PeriodosMoraRequest(tipo,solicitudes(),null)))
                .isInstanceOf(tipo.isEmpty()?ReglaNegocioException.class:RecursoNoEncontradoException.class);
        verify(configuraciones,never()).saveAll(any());verifyNoInteractions(eventos);
    }
}
