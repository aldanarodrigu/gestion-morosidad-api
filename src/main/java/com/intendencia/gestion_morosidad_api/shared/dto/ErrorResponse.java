package com.intendencia.gestion_morosidad_api.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Cuerpo de error común a toda la API.
 *
 * @param errores errores de validación por campo (solo en 400 de validación)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        int status,
        String mensaje,
        Map<String, String> errores,
        LocalDateTime timestamp) {

    public static ErrorResponse de(int status, String mensaje) {
        return new ErrorResponse(status, mensaje, Map.of(), LocalDateTime.now());
    }

    public static ErrorResponse de(int status, String mensaje, Map<String, String> errores) {
        return new ErrorResponse(status, mensaje, errores, LocalDateTime.now());
    }
}
