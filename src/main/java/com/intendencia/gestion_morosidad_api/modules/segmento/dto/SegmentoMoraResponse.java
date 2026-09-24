package com.intendencia.gestion_morosidad_api.modules.segmento.dto;

import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;

public record SegmentoMoraResponse(Long id, String nombre, String descripcion, Integer orden) {

    public static SegmentoMoraResponse de(SegmentoMora segmento) {
        return new SegmentoMoraResponse(
                segmento.getId(), segmento.getNombre(), segmento.getDescripcion(), segmento.getOrden());
    }
}
