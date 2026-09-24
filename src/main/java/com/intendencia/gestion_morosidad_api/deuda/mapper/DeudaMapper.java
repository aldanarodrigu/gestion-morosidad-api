package com.intendencia.gestion_morosidad_api.deuda.mapper;

import com.intendencia.gestion_morosidad_api.deuda.dto.DeudaResponse;
import com.intendencia.gestion_morosidad_api.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class DeudaMapper {

    private DeudaMapper() {
    }

    /** @param hoy fecha de referencia para calcular los días de atraso */
    public static DeudaResponse toResponse(Deuda deuda, LocalDate hoy) {
        Padron padron = deuda.getPadron();
        return DeudaResponse.builder()
                .id(deuda.getId())
                .padron(toPadronResponse(padron))
                .contribuyente(toContribuyenteResponse(padron.getContribuyente()))
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

    private static PadronResponse toPadronResponse(Padron padron) {
        return new PadronResponse(
                padron.getCm(),
                padron.getNumeroPadron(),
                padron.getTipoPadron(),
                padron.getLocalidad(),
                padron.getBlock(),
                padron.getUnidad());
    }

    private static ContribuyenteResponse toContribuyenteResponse(Contribuyente contribuyente) {
        return new ContribuyenteResponse(
                contribuyente.getCm(),
                contribuyente.getNombre(),
                contribuyente.getDocumento());
    }

    private static Long diasAtraso(LocalDate deudaDesde, LocalDate hoy) {
        return deudaDesde == null ? null : ChronoUnit.DAYS.between(deudaDesde, hoy);
    }
}
