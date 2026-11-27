package com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.FechaIntendenciaDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

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
        @JsonProperty("DEUDA_DESDE") @JsonDeserialize(using = FechaIntendenciaDeserializer.Vencimiento.class) LocalDate deudaDesde,
        @JsonProperty("ULTIMO_VENCIMIENTO") @JsonDeserialize(using = FechaIntendenciaDeserializer.Vencimiento.class) LocalDate ultimoVencimiento,
        @JsonProperty("ANIOS_DEUDA") String aniosDeuda,
        @JsonProperty("DEUDA_FACTURAS") BigDecimal deudaFacturas,
        @JsonProperty("SALDO_A_FAVOR") BigDecimal saldoAFavor,
        @JsonProperty("CREDITO_DESCONTADO") BigDecimal creditoDescontado,
        @JsonProperty("MATRICULA") String matricula,
        @JsonProperty("CANT_FACT_VENC") Integer cantidadFacturasVencidas,
        @JsonProperty("CANT_FACT_CON_DEUDA") Integer cantidadFacturasConDeuda,
        @JsonProperty("ANIO_DESDE") Integer anioDesde,
        @JsonProperty("ANIO_HASTA") Integer anioHasta,
        @JsonProperty("CANT_ANIOS_DEUDA") Integer cantidadAniosDeuda) {
    public GeoPagosFacturaPendienteDto(Object cm, Object numeroPadron, Object block, Object unidad, String padtipo, String localidad, String persona, Object documento, Object telefono, String email, String direccion, Object numeroPuerta, String convenio, String tributosDeuda, BigDecimal importeDeuda, LocalDate deudaDesde, LocalDate ultimoVencimiento, String aniosDeuda, BigDecimal deudaFacturas, BigDecimal saldoAFavor, BigDecimal creditoDescontado, String matricula) {
        this(cm, numeroPadron, block, unidad, padtipo, localidad, persona, documento, telefono, email, direccion, numeroPuerta, convenio, tributosDeuda, importeDeuda, deudaDesde, ultimoVencimiento, aniosDeuda, deudaFacturas, saldoAFavor, creditoDescontado, matricula, null, null, null, null, null);
    }

    public GeoPagosFacturaPendienteDto(Object cm, Object numeroPadron, Object block, Object unidad, String padtipo, String localidad, String persona, Object documento, Object telefono, String email, String direccion, Object numeroPuerta, String convenio, String tributosDeuda, BigDecimal importeDeuda, LocalDate deudaDesde, LocalDate ultimoVencimiento, String aniosDeuda, BigDecimal deudaFacturas, BigDecimal saldoAFavor, BigDecimal creditoDescontado) {
        this(cm, numeroPadron, block, unidad, padtipo, localidad, persona, documento, telefono, email, direccion, numeroPuerta, convenio, tributosDeuda, importeDeuda, deudaDesde, ultimoVencimiento, aniosDeuda, deudaFacturas, saldoAFavor, creditoDescontado, null);
    }

    public GeoPagosFacturaPendienteDto(Object cm, Object numeroPadron, Object block, Object unidad,
            String padtipo, String localidad, String persona, Object documento, Object telefono,
            String email, String direccion, Object numeroPuerta, String convenio, String tributosDeuda,
            BigDecimal importeDeuda, LocalDate deudaDesde, LocalDate ultimoVencimiento, String aniosDeuda) {
        this(cm, numeroPadron, block, unidad, padtipo, localidad, persona, documento, telefono,
                email, direccion, numeroPuerta, convenio, tributosDeuda, importeDeuda,
                deudaDesde, ultimoVencimiento, aniosDeuda, null, null, null);
    }
}
