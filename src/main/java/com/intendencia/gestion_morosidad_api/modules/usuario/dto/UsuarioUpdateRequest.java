package com.intendencia.gestion_morosidad_api.modules.usuario.dto;

import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UsuarioUpdateRequest(
        @NotBlank
        @Size(max = 255)
        String username,

        @NotEmpty
        Set<@NotNull Rol> roles,

        @NotNull
        Boolean activo
) {
}