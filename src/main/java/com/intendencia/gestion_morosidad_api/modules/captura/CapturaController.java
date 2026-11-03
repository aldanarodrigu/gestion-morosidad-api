package com.intendencia.gestion_morosidad_api.modules.captura;

import static com.intendencia.gestion_morosidad_api.modules.captura.CapturaDtos.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/capturas-cartera")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR','NOTIFICADOR','ANALISTA')")
public class CapturaController {
    private final CapturaService service;
    @GetMapping public List<Resumen> listar() { return service.listar(); }
    @GetMapping("/{id}") public Detalle obtener(@PathVariable Long id) { return service.obtener(id); }
    @PostMapping public ResponseEntity<Detalle> crear(@Valid @RequestBody Crear peticion, Principal usuario) {
        var detalle = service.crear(peticion,usuario.getName());
        return ResponseEntity.created(java.net.URI.create("/api/capturas-cartera/" + detalle.id())).body(detalle);
    }
}
