package com.intendencia.gestion_morosidad_api.modules.deuda.controller;

import com.intendencia.gestion_morosidad_api.modules.deuda.dto.*;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.CarteraAgrupadaService;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;

@RestController
@RequestMapping("/api/deudas/agrupadas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
public class CarteraAgrupadaController {
    private final CarteraAgrupadaService service;

    @GetMapping
    public PaginaResponse<DeudasAgrupadasResponse> listar(
            @RequestParam(required = false) EstadoDeuda estado,
            @RequestParam(required = false) String padron,
            @RequestParam(required = false) String contribuyente,
            @RequestParam(required = false) String localidad,
            @RequestParam(required = false) Long segmento,
            @RequestParam(required = false) String tributo,
            @RequestParam(required = false) Boolean conContacto,
            @RequestParam(required = false) String tipoPadron,
            @RequestParam(required = false) OrigenDatos origen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 20, sort = "deudaDesde", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.listar(new DeudaFiltro(estado, padron, contribuyente, localidad, segmento, tributo, conContacto, tipoPadron, origen, desde, hasta), pageable);
    }
}
