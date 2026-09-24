package com.intendencia.gestion_morosidad_api.modules.segmento.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Nuevo rango de días para un segmento. Reemplaza al rango activo de ese segmento.
 *
 * @param diasHasta null = sin tope superior
 */
public record ConfiguracionSegmentoRequest(
        @NotNull(message = "El segmento es obligatorio")
        Long segmentoId,

        @NotNull(message = "diasDesde es obligatorio")
        @Min(value = 0, message = "diasDesde no puede ser negativo")
        Integer diasDesde,

        @Min(value = 0, message = "diasHasta no puede ser negativo")
        Integer diasHasta) {
}
