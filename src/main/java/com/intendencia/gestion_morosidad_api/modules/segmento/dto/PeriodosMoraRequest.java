package com.intendencia.gestion_morosidad_api.modules.segmento.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import java.util.List;
public record PeriodosMoraRequest(
        @Size(max = 100) String tipoPadron,
        @NotEmpty List<@NotNull @Valid ConfiguracionSegmentoRequest> rangos,
        // Rechaza clientes antiguos para evitar que una edición por tributo cambie los rangos base.
        @Null(message = "La configuración se realiza por tipoPadron, no por tributoId") Long tributoId) {}
