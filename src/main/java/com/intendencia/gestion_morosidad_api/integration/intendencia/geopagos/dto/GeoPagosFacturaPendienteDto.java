package com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila de GET /api/v1/utec/facturas/pendientes: deuda vencida agrupada por CM.
 * Los identificadores pueden llegar como número o texto (manual, sección 3): se reciben como Object
 * y se convierten a texto al procesarlos. IMPORTE_DEUDA puede venir como texto decimal, número o null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeoPagosFacturaPendienteDto(
        @JsonProperty("CM") Object cm,
        @JsonProperty("NUMERO_PADRON") Object numeroPadron,
        @JsonProperty("BLOCK") Object block,
        @JsonProperty("UNIDAD") Object unidad,
        @JsonProperty("PADTIPO") String padtipo,
        @JsonProperty("LOCALIDAD") String localidad,
        @JsonProperty("PERSONA") String persona,
        @JsonProperty("DOCUMENTO") Object documento,
        @JsonProperty("TELEFONO") Object telefono,
        @JsonProperty("EMAIL") String email,
        @JsonProperty("DIRECCION") String direccion,
        @JsonProperty("NUMERO_PUERTA") Object numeroPuerta,
        @JsonProperty("CONVENIO") String convenio,
        @JsonProperty("TRIBUTOS_DEUDA") String tributosDeuda,
        @JsonProperty("IMPORTE_DEUDA") BigDecimal importeDeuda,
        @JsonProperty("DEUDA_DESDE") LocalDate deudaDesde,
        @JsonProperty("ULTIMO_VENCIMIENTO") LocalDate ultimoVencimiento,
        @JsonProperty("ANIOS_DEUDA") String aniosDeuda) {
}
