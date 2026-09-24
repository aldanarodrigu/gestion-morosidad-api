package com.intendencia.gestion_morosidad_api.deuda.dto;

import com.intendencia.gestion_morosidad_api.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record DeudaResponse(
        Long id,
        PadronResponse padron,
        ContribuyenteResponse contribuyente,
        String tributos,
        BigDecimal importe,
        LocalDate deudaDesde,
        LocalDate ultimoVencimiento,
        Long diasAtraso,
        String aniosDeuda,
        boolean convenio,
        EstadoDeuda estado,
        LocalDateTime fechaSincronizacion) {
}
