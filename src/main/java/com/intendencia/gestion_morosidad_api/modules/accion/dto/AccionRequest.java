package com.intendencia.gestion_morosidad_api.modules.accion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AccionRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,
        @NotNull(message = "El segmento de mora es obligatorio")
        @Positive
        Long segmentoId
) {
}