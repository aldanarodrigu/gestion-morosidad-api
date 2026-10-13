package com.intendencia.gestion_morosidad_api.modules.gestion.dto;

import com.intendencia.gestion_morosidad_api.modules.gestion.ResultadoGestion;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record GestionRequest(

        LocalDateTime fecha,

        @NotNull(message = "El resultado es obligatorio")
        ResultadoGestion resultado,

        @Size(max = 255, message = "La observación no puede superar los 255 caracteres")
        String observacion,

        @NotNull(message = "La deuda es obligatoria")
        @Positive(message = "El identificador debe ser positivo")
        Long deudaId,

        @NotNull(message = "La acción es obligatoria")
        @Positive(message = "El identificador debe ser positivo")
        Long accionId
) {
}