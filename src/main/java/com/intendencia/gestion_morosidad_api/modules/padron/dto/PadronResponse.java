package com.intendencia.gestion_morosidad_api.modules.padron.dto;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;

public record PadronResponse(
        String cm,
        String numeroPadron,
        String tipoPadron,
        String localidad,
        String block,
        String unidad,
        ContribuyenteResponse contribuyente,
        OrigenDatos origen
) {
    public PadronResponse(String cm, String numeroPadron, String tipoPadron, String localidad,
                          String block, String unidad, ContribuyenteResponse contribuyente) {
        this(cm, numeroPadron, tipoPadron, localidad, block, unidad, contribuyente, OrigenDatos.INTENDENCIA);
    }
    public static PadronResponse de(Padron padron) {
        return new PadronResponse(padron.getCm(), padron.getNumeroPadron(),
                padron.getTipoPadron(), padron.getLocalidad(), padron.getBlock(), padron.getUnidad(),
                ContribuyenteResponse.de(padron), padron.getOrigen());
    }
}
