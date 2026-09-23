package com.intendencia.gestion_morosidad_api.deuda.dto;

import com.intendencia.gestion_morosidad_api.deuda.entity.EstadoDeuda;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record DeudaResponse(
        Long id,
        String cm,
        String numeroPadron,
        String block,
        String unidad,
        String localidad,
        String padtipo,
        String contribuyenteNombre,
        String contribuyenteDocumento,
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
