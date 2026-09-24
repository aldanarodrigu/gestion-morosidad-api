package com.intendencia.gestion_morosidad_api.modules.usuario.dto;

import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioUpdateRequest(
        @NotBlank
        String username,

        @NotNull
        Rol rol,

        @NotNull
        Boolean activo
) {
}