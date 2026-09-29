package com.intendencia.gestion_morosidad_api.modules.contacto.dto;

import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContactoRequest(

        Long id,

        @NotNull
        TipoContacto tipoContacto,

        @NotBlank
        String valor,

        @NotNull
        Boolean esDeContribuyente,

        @NotBlank
        String origen

) {
}