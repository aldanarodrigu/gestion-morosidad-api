package com.intendencia.gestion_morosidad_api.deuda.mapper;

import com.intendencia.gestion_morosidad_api.deuda.dto.DeudaResponse;
import com.intendencia.gestion_morosidad_api.deuda.entity.Deuda;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class DeudaMapper {

    private DeudaMapper() {
    }

    /** @param hoy fecha de referencia para calcular los días de atraso */
    public static DeudaResponse toResponse(Deuda deuda, LocalDate hoy) {
        return DeudaResponse.builder()
                .id(deuda.getId())
                .cm(deuda.getCm())
                .numeroPadron(deuda.getNumeroPadron())
                .block(deuda.getBlock())
                .unidad(deuda.getUnidad())
                .localidad(deuda.getLocalidad())
                .padtipo(deuda.getPadtipo())
                .contribuyenteNombre(deuda.getContribuyenteNombre())
                .contribuyenteDocumento(deuda.getContribuyenteDocumento())
                .tributos(deuda.getTributos())
                .importe(deuda.getImporte())
                .deudaDesde(deuda.getDeudaDesde())
                .ultimoVencimiento(deuda.getUltimoVencimiento())
                .diasAtraso(diasAtraso(deuda.getDeudaDesde(), hoy))
                .aniosDeuda(deuda.getAniosDeuda())
                .convenio(deuda.isConvenio())
                .estado(deuda.getEstado())
                .fechaSincronizacion(deuda.getFechaSincronizacion())
                .build();
    }

    private static Long diasAtraso(LocalDate deudaDesde, LocalDate hoy) {
        return deudaDesde == null ? null : ChronoUnit.DAYS.between(deudaDesde, hoy);
    }
}
