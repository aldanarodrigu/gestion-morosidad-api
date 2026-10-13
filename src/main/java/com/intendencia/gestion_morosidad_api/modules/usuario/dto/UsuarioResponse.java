package com.intendencia.gestion_morosidad_api.modules.usuario.dto;

import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import java.util.Set;

public record UsuarioResponse(
        Long id,
        String username,
        Set<Rol> roles,
        Boolean activo
) {
}