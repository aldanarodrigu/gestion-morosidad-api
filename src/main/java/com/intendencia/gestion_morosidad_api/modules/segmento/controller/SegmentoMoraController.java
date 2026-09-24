package com.intendencia.gestion_morosidad_api.modules.segmento.controller;

import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraRequest;
import com.intendencia.gestion_morosidad_api.modules.segmento.dto.SegmentoMoraResponse;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.SegmentoMoraService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/segmentos-mora")
@RequiredArgsConstructor
public class SegmentoMoraController {

    private final SegmentoMoraService segmentoMoraService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public List<SegmentoMoraResponse> listar() {
        return segmentoMoraService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public SegmentoMoraResponse crear(@Valid @RequestBody SegmentoMoraRequest request) {
        return segmentoMoraService.crear(request);
    }
}
