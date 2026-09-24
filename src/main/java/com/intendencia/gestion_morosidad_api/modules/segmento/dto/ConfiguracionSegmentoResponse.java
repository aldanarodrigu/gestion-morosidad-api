package com.intendencia.gestion_morosidad_api.modules.segmento.dto;

import com.intendencia.gestion_morosidad_api.modules.segmento.entity.ConfiguracionSegmento;
import java.time.LocalDateTime;

public record ConfiguracionSegmentoResponse(
        Long id,
        Long segmentoId,
        String segmentoNombre,
        Integer diasDesde,
        Integer diasHasta,
        boolean activa,
        LocalDateTime fechaCreacion) {

    public static ConfiguracionSegmentoResponse de(ConfiguracionSegmento configuracion) {
        return new ConfiguracionSegmentoResponse(
                configuracion.getId(),
                configuracion.getSegmento().getId(),
                configuracion.getSegmento().getNombre(),
                configuracion.getDiasDesde(),
                configuracion.getDiasHasta(),
                configuracion.isActiva(),
                configuracion.getFechaCreacion());
    }
}
