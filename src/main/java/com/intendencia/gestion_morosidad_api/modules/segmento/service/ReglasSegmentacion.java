package com.intendencia.gestion_morosidad_api.modules.segmento.service;

import com.intendencia.gestion_morosidad_api.modules.segmento.entity.ConfiguracionSegmento;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Rangos vigentes precargados: cada padrón usa los rangos de su PadTipo o la base. */
public final class ReglasSegmentacion {
    private final List<ConfiguracionSegmento> base;
    private final Map<String, List<ConfiguracionSegmento>> porTipoPadron = new HashMap<>();

    public ReglasSegmentacion(List<ConfiguracionSegmento> configuracionesActivas) {
        base = configuracionesActivas.stream()
                .filter(c -> c.getTributo() == null && c.getTipoPadron() == null).toList();
        for (var configuracion : configuracionesActivas) {
            if (configuracion.getTributo() == null && configuracion.getTipoPadron() != null)
                porTipoPadron.computeIfAbsent(configuracion.getTipoPadron(), k -> new ArrayList<>()).add(configuracion);
        }
    }

    public SegmentoMora segmentoPara(LocalDate deudaDesde, LocalDate hoy) {
        return buscar(base, deudaDesde, hoy);
    }

    /** Los tributos concretos de la deuda no cambian la configuración del PadTipo. */
    public SegmentoMora segmentoPara(LocalDate deudaDesde, LocalDate hoy, String tipoPadron) {
        return buscar(tipoPadron == null ? base : porTipoPadron.getOrDefault(tipoPadron.trim(), base), deudaDesde, hoy);
    }

    private static SegmentoMora buscar(List<ConfiguracionSegmento> rangos, LocalDate desde, LocalDate hoy) {
        if (desde == null) return null;
        long dias = ChronoUnit.DAYS.between(desde, hoy);
        return rangos.stream().filter(c -> c.contiene(dias)).map(ConfiguracionSegmento::getSegmento).findFirst().orElse(null);
    }
}
