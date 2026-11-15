package com.intendencia.gestion_morosidad_api.modules.deuda.controller;
import com.intendencia.gestion_morosidad_api.modules.deuda.dto.HistorialPadronResponse;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.HistorialPadronService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/deudas")
@RequiredArgsConstructor
public class HistorialPadronController {
    private final HistorialPadronService historial;
    @GetMapping("/{id}/historial-contribuyentes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public HistorialPadronResponse consultar(@PathVariable Long id) { return historial.consultar(id); }
}
