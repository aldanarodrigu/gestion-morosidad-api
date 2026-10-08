package com.intendencia.gestion_morosidad_api.modules.gestion.dto;

import com.intendencia.gestion_morosidad_api.modules.gestion.ResultadoGestion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record GestionRequest(

        @NotNull(message = "La fecha es obligatoria")
        LocalDateTime fecha,

        @NotNull(message = "El resultado es obligatorio")
        ResultadoGestion resultado,

        @Size(max = 255, message = "La observación no puede superar los 255 caracteres")
        String observacion,

        @NotNull(message = "La deuda es obligatoria")
        Long deudaId,

        @NotNull(message = "La acción es obligatoria")
        Long accionId
) {
}