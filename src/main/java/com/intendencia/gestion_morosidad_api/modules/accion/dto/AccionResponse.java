package com.intendencia.gestion_morosidad_api.modules.accion.dto;

import com.intendencia.gestion_morosidad_api.modules.accion.Accion;

public record AccionResponse(
        Long id,
        String nombre,
        Boolean activo
){}