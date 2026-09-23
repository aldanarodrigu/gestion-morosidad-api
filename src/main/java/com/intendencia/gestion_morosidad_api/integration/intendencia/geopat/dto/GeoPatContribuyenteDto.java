package com.intendencia.gestion_morosidad_api.integration.intendencia.geopat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeoPatContribuyenteDto(

        @JsonProperty("CM")
        Object cm,

        @JsonProperty("NUMERO_PADRON")
        Object numeroPadron,

        @JsonProperty("TIPO_PADRON")
        String tipoPadron,

        @JsonProperty("LOCALIDAD")
        String localidad,

        @JsonProperty("BLOCK")
        Object block,

        @JsonProperty("UNIDAD")
        Object unidad,

        @JsonProperty("NOMBRE_PERSONAS")
        String nombrePersonas,

        @JsonProperty("TELEFONO_PERSONAS")
        String telefonoPersonas,

        @JsonProperty("EMAIL_PERSONAS")
        String emailPersonas

) {
}