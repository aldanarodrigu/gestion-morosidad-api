package com.intendencia.gestion_morosidad_api.modules.segmento.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.intendencia.gestion_morosidad_api.modules.segmento.entity.ConfiguracionSegmento;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReglasSegmentacionTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 24);

    private final ReglasSegmentacion reglas = new ReglasSegmentacion(List.of(
            configuracion("Mora Temprana", 0, 90),
            configuracion("Mora Inicial", 91, 365),
            configuracion("Mora Tardía", 366, null)));

    @ParameterizedTest(name = "{0} días de atraso -> {1}")
    @CsvSource({
            "0, Mora Temprana",
            "90, Mora Temprana",
            "91, Mora Inicial",
            "365, Mora Inicial",
            "366, Mora Tardía",
            "3000, Mora Tardía"})
    void asignaElSegmentoSegunLosLimitesDelRango(int dias, String esperado) {
        assertThat(reglas.segmentoPara(HOY.minusDays(dias), HOY).getNombre()).isEqualTo(esperado);
    }

    @Test
    void sinFechaDeVencimientoNoAsignaSegmento() {
        assertThat(reglas.segmentoPara(null, HOY)).isNull();
    }

    @Test
    void vencimientoFuturoNoAsignaSegmento() {
        assertThat(reglas.segmentoPara(HOY.plusDays(5), HOY)).isNull();
    }

    @Test
    void diasEnUnHuecoEntreRangosNoAsignaSegmento() {
        ReglasSegmentacion conHueco = new ReglasSegmentacion(List.of(
                configuracion("A", 0, 60), configuracion("B", 91, null)));

        assertThat(conHueco.segmentoPara(HOY.minusDays(75), HOY)).isNull();
    }

    private static ConfiguracionSegmento configuracion(String nombre, int desde, Integer hasta) {
        SegmentoMora segmento = new SegmentoMora();
        segmento.setNombre(nombre);
        ConfiguracionSegmento configuracion = new ConfiguracionSegmento();
        configuracion.setSegmento(segmento);
        configuracion.setDiasDesde(desde);
        configuracion.setDiasHasta(hasta);
        return configuracion;
    }

    @Test
    void rangosPorPadTipoNoContaminanLaBaseNiOtrosTipos() {
        var base = configuracion("Base", 0, null);
        var ciu = configuracion("CIU propio", 0, 120);
        ciu.setTipoPadron("CIU");
        var ciuTardia = configuracion("CIU tardía", 121, null);
        ciuTardia.setTipoPadron("CIU");
        var reglas = new ReglasSegmentacion(List.of(base, ciu, ciuTardia));
        assertThat(reglas.segmentoPara(HOY.minusDays(120), HOY, "CIU").getNombre()).isEqualTo("CIU propio");
        assertThat(reglas.segmentoPara(HOY.minusDays(121), HOY, " CIU ").getNombre()).isEqualTo("CIU tardía");
        assertThat(reglas.segmentoPara(HOY.minusDays(121), HOY, "CIR").getNombre()).isEqualTo("Base");
        assertThat(reglas.segmentoPara(HOY.minusDays(121), HOY, null).getNombre()).isEqualTo("Base");
    }

    @Test
    void ignoraConfiguracionesHistoricasPorTributo() {
        var antigua = configuracion("Antigua", 0, null);
        antigua.setTributo(new com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo());
        var base = configuracion("Base", 0, null);
        var reglas = new ReglasSegmentacion(List.of(antigua, base));
        assertThat(reglas.segmentoPara(HOY.minusDays(100), HOY, "CIU").getNombre()).isEqualTo("Base");
    }
}
