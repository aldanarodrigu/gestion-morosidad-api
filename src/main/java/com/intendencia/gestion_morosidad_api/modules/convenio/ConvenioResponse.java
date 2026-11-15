package com.intendencia.gestion_morosidad_api.modules.convenio;
import com.fasterxml.jackson.annotation.*;
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConvenioResponse(
    @JsonProperty("CM") Long CM,
    @JsonProperty("NUMERO_CONVENIO") String NUMERO_CONVENIO,
    @JsonProperty("MATRICULA") String MATRICULA,
    @JsonProperty("LOCALIDAD") String LOCALIDAD,
    @JsonProperty("PERSONA") String PERSONA,
    @JsonProperty("DOCUMENTO") String DOCUMENTO,
    @JsonProperty("TELEFONO") String TELEFONO,
    @JsonProperty("EMAIL") String EMAIL,
    @JsonProperty("DIRECCION") String DIRECCION,
    @JsonProperty("NUMERO_PUERTA") String NUMERO_PUERTA,
    @JsonProperty("TRIBUTOS_DEUDA") String TRIBUTOS_DEUDA,
    @JsonProperty("IMPORTE_DEUDA") String IMPORTE_DEUDA,
    @JsonProperty("DEUDA_DESDE") String DEUDA_DESDE,
    @JsonProperty("ULTIMO_VENCIMIENTO") String ULTIMO_VENCIMIENTO,
    @JsonProperty("ANIOS_DEUDA") String ANIOS_DEUDA,
    @JsonProperty("COD_CONTRIBUYENTE") Long COD_CONTRIBUYENTE,
    @JsonProperty("ANIO_DESDE") Integer ANIO_DESDE,
    @JsonProperty("ANIO_HASTA") Integer ANIO_HASTA,
    @JsonProperty("CANT_ANIOS_DEUDA") Integer CANT_ANIOS_DEUDA,
    @JsonProperty("DEUDA_FACTURAS") String DEUDA_FACTURAS,
    @JsonProperty("SALDO_A_FAVOR") String SALDO_A_FAVOR,
    @JsonProperty("CREDITO_DESCONTADO") String CREDITO_DESCONTADO,
    @JsonProperty("CANT_CUOTAS_VENC") Integer CANT_CUOTAS_VENC,
    @JsonProperty("CANT_CUOTAS_CONVENIOS") Integer CANT_CUOTAS_CONVENIOS) {}
