package com.intendencia.gestion_morosidad_api.modules.tributo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TributoUpdateRequest(
        @NotBlank @Size(max = 200) String descripcion
) {}
