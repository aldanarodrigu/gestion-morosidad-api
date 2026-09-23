package com.intendencia.gestion_morosidad_api.modules.contribuyente.dto;

public record ContribuyenteResponse(
        String cm,
        String nombre,
        String documento
) {
}