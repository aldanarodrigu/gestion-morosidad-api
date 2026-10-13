package com.intendencia.gestion_morosidad_api.modules.deuda.dto;

import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraResponse;
import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoResponse;
import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)

public record DeudaResponse(
        Long id,
        PadronResponse padron,
        ContribuyenteResponse contribuyente,
        List<TributoResponse> tributos,
        BigDecimal importe,
        LocalDate deudaDesde,
        LocalDate ultimoVencimiento,
        Long diasAtraso,
        String aniosDeuda,
        boolean convenio,
        SegmentoMoraResponse segmentoMora,
        LocalDate fechaUltimoCobro,
        BigDecimal importeUltimoCobro,
        EstadoDeuda estado,
        LocalDateTime fechaSincronizacion,
        List<AccionResponse> accionesSugeridas,
        Long cantidadGestiones,
        Long contribuyenteId,
        boolean conContacto) {
}
