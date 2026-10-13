package com.intendencia.gestion_morosidad_api.modules.usuario.dto;

import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UsuarioRequest(
        @NotBlank
        @Size(max = 255)
        String username,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotEmpty
        Set<@NotNull Rol> roles,

        Boolean activo
) {
}