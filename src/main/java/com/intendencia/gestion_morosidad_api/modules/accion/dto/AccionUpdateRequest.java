package com.intendencia.gestion_morosidad_api.modules.accion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccionUpdateRequest(

        @NotNull(message = "El estado activo es obligatorio")
        Boolean activo
) {
}