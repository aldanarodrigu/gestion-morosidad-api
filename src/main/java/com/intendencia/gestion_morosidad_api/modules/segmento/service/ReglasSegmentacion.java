package com.intendencia.gestion_morosidad_api.modules.segmento.service;

import com.intendencia.gestion_morosidad_api.modules.segmento.entity.ConfiguracionSegmento;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Foto de los rangos activos, para asignar segmentos a muchas deudas sin consultar la base por cada una.
 * Los días de atraso se cuentan desde el vencimiento impago más antiguo (deudaDesde), igual que diasAtraso.
 */
public record ReglasSegmentacion(List<ConfiguracionSegmento> configuracionesActivas) {

    /** @return el segmento que corresponde, o null si no hay fecha o ningún rango contiene los días */
    public SegmentoMora segmentoPara(LocalDate deudaDesde, LocalDate hoy) {
        if (deudaDesde == null) {
            return null;
        }
        long dias = ChronoUnit.DAYS.between(deudaDesde, hoy);
        return configuracionesActivas.stream()
                .filter(configuracion -> configuracion.contiene(dias))
                .map(ConfiguracionSegmento::getSegmento)
                .findFirst()
                .orElse(null);
    }
}
