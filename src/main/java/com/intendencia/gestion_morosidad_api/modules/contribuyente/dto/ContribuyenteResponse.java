package com.intendencia.gestion_morosidad_api.modules.contribuyente.dto;

import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;

public record ContribuyenteResponse(
        String cm,
        String nombre,
        String documento
) {
    /** El CM se conserva en la respuesta como identificador del padrón consultado. */
    public static ContribuyenteResponse de(Padron padron) {
        return new ContribuyenteResponse(
                padron.getCm(), padron.getContribuyente().getNombre(),
                padron.getContribuyente().getDocumento());
    }
}
