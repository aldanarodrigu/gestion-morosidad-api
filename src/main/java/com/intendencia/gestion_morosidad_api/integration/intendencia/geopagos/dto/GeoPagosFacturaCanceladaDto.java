package com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Fila de GET /api/v1/utec/facturas/canceladas: último cobro de cada CM dentro del intervalo pedido. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeoPagosFacturaCanceladaDto(
        @JsonProperty("CM") Object cm,
        @JsonProperty("NUMERO_PADRON") Object numeroPadron,
        @JsonProperty("NUMERO_COBRO") Object numeroCobro,
        @JsonProperty("FECHA_COBRO") LocalDate fechaCobro,
        @JsonProperty("IMPORTE_TOTAL") BigDecimal importeTotal) {
}
