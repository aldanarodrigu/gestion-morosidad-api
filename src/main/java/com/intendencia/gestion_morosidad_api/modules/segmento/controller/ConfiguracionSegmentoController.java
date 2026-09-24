package com.intendencia.gestion_morosidad_api.modules.segmento.controller;

import com.intendencia.gestion_morosidad_api.modules.segmento.dto.ConfiguracionSegmentoRequest;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.ConfiguracionSegmentoResponse;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ConfiguracionSegmentoService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/configuraciones-segmento")
@RequiredArgsConstructor
public class ConfiguracionSegmentoController {

    private final ConfiguracionSegmentoService configuracionService;

    /** Rangos vigentes; con ?historial=true incluye los reemplazados (auditoría de cambios). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public List<ConfiguracionSegmentoResponse> listar(@RequestParam(defaultValue = "false") boolean historial) {
        return configuracionService.listar(historial);
    }

    /** Asigna un nuevo rango a un segmento (reemplaza el vigente) y recalcula los segmentos de las deudas. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ConfiguracionSegmentoResponse crear(@Valid @RequestBody ConfiguracionSegmentoRequest request) {
        return configuracionService.crear(request);
    }
}
