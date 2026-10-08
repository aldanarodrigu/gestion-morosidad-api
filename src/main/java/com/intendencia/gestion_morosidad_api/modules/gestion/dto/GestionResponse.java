package com.intendencia.gestion_morosidad_api.modules.gestion.dto;

import com.intendencia.gestion_morosidad_api.modules.gestion.ResultadoGestion;

import java.time.LocalDateTime;

public record GestionResponse(
        Long id,
        LocalDateTime fecha,
        ResultadoGestion resultado,
        String observacion,
        Long deudaId,
        Long accionId,
        String accionNombre
) {
}