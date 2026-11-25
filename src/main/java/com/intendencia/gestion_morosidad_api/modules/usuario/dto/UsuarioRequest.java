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
        // Chequeo rápido con error por campo; PoliticaPassword valida en el servicio los 15 caracteres
        // Unicode (@Size cuenta unidades UTF-16) y los 72 bytes.
        @Size(min = 15, max = 72)
        String password,

        @NotEmpty
        Set<@NotNull Rol> roles,

        Boolean activo
) {
}