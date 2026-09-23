package com.intendencia.gestion_morosidad_api.modules.padron.dto;

public record PadronResponse(
        String cm,
        String numeroPadron,
        String tipoPadron,
        String localidad,
        String block,
        String unidad
) {
}