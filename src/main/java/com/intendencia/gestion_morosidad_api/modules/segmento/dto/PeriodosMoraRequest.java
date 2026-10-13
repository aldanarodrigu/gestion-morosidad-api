package com.intendencia.gestion_morosidad_api.modules.segmento.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
public record PeriodosMoraRequest(@jakarta.validation.constraints.Positive Long tributoId, @NotEmpty List<@NotNull @Valid ConfiguracionSegmentoRequest> rangos) {}
