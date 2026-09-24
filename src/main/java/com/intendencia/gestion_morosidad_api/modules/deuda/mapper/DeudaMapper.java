package com.intendencia.gestion_morosidad_api.modules.deuda.mapper;

import com.intendencia.gestion_morosidad_api.modules.deuda.dto.DeudaResponse;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraResponse;
import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class DeudaMapper {

    private DeudaMapper() {
    }

    /** @param hoy fecha de referencia para calcular los días de atraso */
    public static DeudaResponse toResponse(Deuda deuda, LocalDate hoy) {
        Padron padron = deuda.getPadron();
        PadronResponse padronResponse = PadronResponse.de(padron);
        return DeudaResponse.builder()
                .id(deuda.getId())
                .padron(padronResponse)
                .contribuyente(padronResponse.contribuyente())
                .tributos(toTributosResponse(deuda))
                .importe(deuda.getImporte())
                .deudaDesde(deuda.getDeudaDesde())
                .ultimoVencimiento(deuda.getUltimoVencimiento())
                .diasAtraso(diasAtraso(deuda.getDeudaDesde(), hoy))
                .aniosDeuda(deuda.getAniosDeuda())
                .convenio(deuda.isConvenio())
                .segmentoMora(deuda.getSegmentoMora() == null ? null : SegmentoMoraResponse.de(deuda.getSegmentoMora()))
                .fechaUltimoCobro(deuda.getFechaUltimoCobro())
                .importeUltimoCobro(deuda.getImporteUltimoCobro())
                .estado(deuda.getEstado())
                .fechaSincronizacion(deuda.getFechaSincronizacion())
                .build();
    }

    /** Tributos de la deuda ordenados por código. */
    public static List<TributoResponse> toTributosResponse(Deuda deuda) {
        return deuda.getTributos().stream()
                .sorted(TributoResponse.POR_CODIGO)
                .map(TributoResponse::de)
                .toList();
    }

    private static Long diasAtraso(LocalDate deudaDesde, LocalDate hoy) {
        return deudaDesde == null ? null : ChronoUnit.DAYS.between(deudaDesde, hoy);
    }
}
