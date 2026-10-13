package com.intendencia.gestion_morosidad_api.modules.deuda.dto;

import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraResponse;
import java.math.BigDecimal;
import java.util.List;

/** Totales de las deudas que cumplen los filtros, agrupadas por documento. */
public record DeudasAgrupadasResponse(
        String clave, String documento, List<String> nombres,
        BigDecimal importeTotal, Long diasAtrasoMaximos,
        SegmentoMoraResponse segmentoMora, List<DeudaResponse> deudas) {
}
