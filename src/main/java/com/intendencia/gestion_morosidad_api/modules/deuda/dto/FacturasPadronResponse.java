package com.intendencia.gestion_morosidad_api.modules.deuda.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Montos como texto: conserva los decimales y las unidades de la fuente. */
public record FacturasPadronResponse(List<Factura> data, Meta meta) {
    public record Meta(Long total) {}
    public record MontoUnidad(@JsonProperty("unidad_codigo") Long unidadCodigo, String monto, String moneda) {}
    public record Factura(
            @JsonProperty("factura_id") String facturaId, Long cm, String tributo, Integer anio,
            Integer cuota, @JsonProperty("total_cuotas") Integer totalCuotas,
            String monto, String moneda, String estado, Boolean vencida,
            @JsonProperty("fecha_vencimiento") String fechaVencimiento,
            @JsonProperty("periodo_desde") String periodoDesde,
            @JsonProperty("periodo_hasta") String periodoHasta,
            @JsonProperty("convenio_cm") Long convenioCm,
            @JsonProperty("unidad_codigo") Long unidadCodigo,
            @JsonProperty("montos_por_unidad") List<MontoUnidad> montosPorUnidad) {}
}
