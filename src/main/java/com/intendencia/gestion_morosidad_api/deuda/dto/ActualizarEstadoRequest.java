package com.intendencia.gestion_morosidad_api.deuda.dto;

import com.intendencia.gestion_morosidad_api.deuda.entity.EstadoDeuda;
import jakarta.validation.constraints.NotNull;

public record ActualizarEstadoRequest(
        @NotNull(message = "El estado es obligatorio")
        EstadoDeuda estado) {
}
