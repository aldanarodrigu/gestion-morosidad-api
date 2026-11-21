package com.intendencia.gestion_morosidad_api.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Size(max = 255)
        String username,
        @NotBlank
        String password
) {
}
