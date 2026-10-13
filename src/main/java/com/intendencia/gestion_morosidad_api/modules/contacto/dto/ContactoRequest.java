package com.intendencia.gestion_morosidad_api.modules.contacto.dto;

import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public record ContactoRequest(

        @Positive
        Long id,

        @NotNull
        TipoContacto tipoContacto,

        @NotBlank
        @Size(max = 255)
        String valor,

        @NotNull
        Boolean esDeContribuyente,

        @NotBlank
        @Size(max = 255)
        String origen

) {
}