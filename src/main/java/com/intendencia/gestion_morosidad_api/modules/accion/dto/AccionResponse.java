package com.intendencia.gestion_morosidad_api.modules.accion.dto;

import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraResponse;
import java.util.List;

public record AccionResponse(Long id, String nombre, Boolean activo, List<SegmentoMoraResponse> segmentos) {
    public AccionResponse(Long id, String nombre, Boolean activo) {
        this(id, nombre, activo, List.of());
    }
}
