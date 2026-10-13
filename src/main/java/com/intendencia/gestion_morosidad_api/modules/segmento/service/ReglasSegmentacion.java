package com.intendencia.gestion_morosidad_api.modules.segmento.service;

import com.intendencia.gestion_morosidad_api.modules.segmento.entity.ConfiguracionSegmento;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Rangos vigentes precargados: cada tributo usa sus rangos propios o la base. */
public final class ReglasSegmentacion {
    private final List<ConfiguracionSegmento> base;
    private final Map<Long, List<ConfiguracionSegmento>> porTributo = new HashMap<>();

    public ReglasSegmentacion(List<ConfiguracionSegmento> configuracionesActivas) {
        base = configuracionesActivas.stream().filter(c -> c.getTributo() == null).toList();
        for (var configuracion : configuracionesActivas) {
            if (configuracion.getTributo() != null)
                porTributo.computeIfAbsent(configuracion.getTributo().getId(), k -> new ArrayList<>()).add(configuracion);
        }
    }

    public SegmentoMora segmentoPara(LocalDate deudaDesde, LocalDate hoy) {
        return buscar(base, deudaDesde, hoy);
    }

    /** Una deuda con varios tributos toma el segmento mas avanzado por orden. */
    public SegmentoMora segmentoPara(LocalDate deudaDesde, LocalDate hoy, Collection<Tributo> tributos) {
        if (tributos == null || tributos.isEmpty()) return segmentoPara(deudaDesde, hoy);
        return tributos.stream()
                .map(t -> buscar(porTributo.getOrDefault(t.getId(), base), deudaDesde, hoy))
                .filter(Objects::nonNull)
                .max(Comparator.comparing(SegmentoMora::getOrden, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static SegmentoMora buscar(List<ConfiguracionSegmento> rangos, LocalDate desde, LocalDate hoy) {
        if (desde == null) return null;
        long dias = ChronoUnit.DAYS.between(desde, hoy);
        return rangos.stream().filter(c -> c.contiene(dias)).map(ConfiguracionSegmento::getSegmento).findFirst().orElse(null);
    }
}
