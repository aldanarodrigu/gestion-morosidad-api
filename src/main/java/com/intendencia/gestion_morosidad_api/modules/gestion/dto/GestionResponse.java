package com.intendencia.gestion_morosidad_api.modules.gestion.dto;

import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoResponse;
import com.intendencia.gestion_morosidad_api.modules.gestion.ResultadoGestion;

import java.time.LocalDateTime;
import java.util.List;

public record GestionResponse(
        Long id,
        LocalDateTime fecha,
        ResultadoGestion resultado,
        String observacion,
        Long deudaId,
        Long accionId,
        String accionNombre,
        Long usuarioId,
        String usuarioNombre,
        String cm,
        String numeroPadron,
        List<TributoResponse> tributos
) {
}