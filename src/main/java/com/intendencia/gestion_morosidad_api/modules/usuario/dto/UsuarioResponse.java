package com.intendencia.gestion_morosidad_api.modules.usuario.dto;

import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;

public record UsuarioResponse(
        Long id,
        String username,
        Rol rol,
        Boolean activo
) {
}