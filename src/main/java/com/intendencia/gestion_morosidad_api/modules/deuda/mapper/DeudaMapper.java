package com.intendencia.gestion_morosidad_api.modules.deuda.mapper;

import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionResponse;
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

    /** @param hoy fecha de referencia para calcular los dÃ­as de atraso */
    public static DeudaResponse toResponse(Deuda deuda, LocalDate hoy, List<AccionResponse> accionesSugeridas) {
        Padron padron = deuda.getPadron();
        PadronResponse padronResponse = PadronResponse.de(padron);
        return DeudaResponse.builder()
                .id(deuda.getId())
                .enPeriodoGestion(deuda.isEnPeriodoGestion()).anioPeriodo(deuda.getAnioPeriodo())
                .padron(padronResponse)
                .contribuyente(padronResponse.contribuyente())
                .tributos(toTributosResponse(deuda))
                .importe(deuda.getImporte())
                .deudaFacturas(deuda.getDeudaFacturas())
                .saldoAFavor(deuda.getSaldoAFavor())
                .creditoDescontado(deuda.getCreditoDescontado())

                .deudaDesde(deuda.getDeudaDesde())
                .ultimoVencimiento(deuda.getUltimoVencimiento())
                .diasAtraso(diasAtraso(deuda.getDeudaDesde(), hoy))
                .aniosDeuda(deuda.getAniosDeuda())
                .cantidadFacturasVencidas(deuda.getCantidadFacturasVencidas())
                .cantidadFacturasConDeuda(deuda.getCantidadFacturasConDeuda())
                .anioDesde(deuda.getAnioDesde())
                .anioHasta(deuda.getAnioHasta())
                .cantidadAniosDeuda(deuda.getCantidadAniosDeuda())

                .convenio(deuda.isConvenio())
                .segmentoMora(deuda.getSegmentoMora() == null ? null : SegmentoMoraResponse.de(deuda.getSegmentoMora()))
                .fechaUltimoCobro(deuda.getFechaUltimoCobro())
                .importeUltimoCobro(deuda.getImporteUltimoCobro())
                .motivoUltimoCobro(deuda.getMotivoUltimoCobro())
                .estado(deuda.getEstado())
                .fechaSincronizacion(deuda.getFechaSincronizacion())
                .accionesSugeridas(accionesSugeridas)
                .contribuyenteId(deuda.getPadron().getContribuyente().getId())
                .conContacto(padron.getContribuyente().getContactos().stream()
                        .anyMatch(c -> c.getValor() != null && !c.getValor().trim().isEmpty()))
                .build();
    }

    /** Tributos de la deuda ordenados por cÃ³digo. */
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
