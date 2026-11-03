package com.intendencia.gestion_morosidad_api.modules.deuda.controller;

import com.intendencia.gestion_morosidad_api.modules.deuda.dto.FacturasPadronResponse;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.FacturasPadronService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deudas")
@RequiredArgsConstructor
public class FacturasPadronController {
    private final FacturasPadronService facturas;

    @GetMapping("/{id}/facturas")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public FacturasPadronResponse consultar(@PathVariable Long id,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Boolean vencida,
            @RequestParam(defaultValue = "true") boolean incluirConvenios) {
        return facturas.consultar(id, estado, vencida, incluirConvenios);
    }
}
