package com.intendencia.gestion_morosidad_api.modules.sincronizacion.controller;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.service.*;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
import java.util.UUID;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/sincronizaciones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ConfiguracionSincronizacionController {
    private final PeriodoSincronizacionService periodo;
    private final SincronizacionManualService manual;
    public record Configuracion(Integer anioMinimo) {}
    @GetMapping("/configuracion") public Configuracion obtener() { return new Configuracion(periodo.anioMinimo()); }
    @PutMapping("/configuracion") public Configuracion guardar(@Valid @RequestBody Configuracion config) {
        manual.guardar(config.anioMinimo()); return obtener();
    }
    @PostMapping("/ejecucion") public ResponseEntity<SincronizacionManualService.Ejecucion> iniciar(
            @RequestParam(defaultValue="INTENDENCIA") OrigenDatos origen) {
        return ResponseEntity.accepted().body(manual.iniciar(origen));
    }
    public record CancelarEjecucion(UUID id) {}
    @PostMapping("/ejecucion/cancelar") public ResponseEntity<SincronizacionManualService.Ejecucion> cancelar(
            @RequestBody(required = false) CancelarEjecucion ejecucion) {
        if (ejecucion == null || ejecucion.id() == null)
            throw new ReglaNegocioException("Indicá el identificador de la sincronización que querés cancelar.");
        return ResponseEntity.accepted().body(manual.cancelar(ejecucion.id()));
    }
    @GetMapping("/ejecucion") public ResponseEntity<SincronizacionManualService.Ejecucion> estado() {
        var estado = manual.estado();
        return estado == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(estado);
    }
}
