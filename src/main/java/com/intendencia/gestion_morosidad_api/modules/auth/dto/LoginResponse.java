package com.intendencia.gestion_morosidad_api.modules.auth.dto;

import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import java.util.Set;

public record LoginResponse(
        String token,
        Long id,
        String username,
        Set<Rol> roles
) {
}