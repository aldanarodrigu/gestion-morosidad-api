package com.intendencia.gestion_morosidad_api.modules.deuda.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
public record HistorialPadronResponse(List<Relacion> data, Meta meta) {
    public record Meta(Long total) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Relacion(
        @JsonProperty("CM_PADRON") Long cmPadron,
        @JsonProperty("NRO_PADRON") String numeroPadron,
        @JsonProperty("TIPO_PADRON") String tipoPadron,
        @JsonProperty("COD_CONTRIBUYENTE") Long codigoContribuyente,
        @JsonProperty("NOMBRE") String nombre,
        @JsonProperty("DOCUMENTO") String documento,
        @JsonProperty("TELEFONO") String telefono,
        @JsonProperty("EMAIL") String email,
        @JsonProperty("DESCRIPCION_RELACION") String descripcionRelacion,
        @JsonProperty("SITUACION_REGISTRO") String situacionRegistro) {}
}
