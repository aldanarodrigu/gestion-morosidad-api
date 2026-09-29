package com.intendencia.gestion_morosidad_api.modules.auth.dto;

import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;

public record LoginResponse(
        String token,
        Long id,
        String username,
        Rol rol
) {
}