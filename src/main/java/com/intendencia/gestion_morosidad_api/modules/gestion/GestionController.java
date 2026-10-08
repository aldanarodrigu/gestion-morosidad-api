package com.intendencia.gestion_morosidad_api.modules.gestion;

import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionResponse;
import com.intendencia.gestion_morosidad_api.modules.gestion.dto.GestionRequest;
import com.intendencia.gestion_morosidad_api.modules.gestion.dto.GestionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gestiones")
public class GestionController {

    private final GestionService gestionService;

    public GestionController(GestionService gestionService) {
        this.gestionService = gestionService;
    }

    @GetMapping
    public ResponseEntity<List<GestionResponse>> getAll() {
        return ResponseEntity.ok(
                gestionService.getAllGestiones()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<GestionResponse> getById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                gestionService.getGestionById(id)
        );
    }

    @GetMapping("/deuda/{deudaId}/acciones-disponibles")
    public ResponseEntity<List<AccionResponse>> getAccionesDisponibles(
            @PathVariable Long deudaId
    ) {
        return ResponseEntity.ok(
                gestionService.getAccionesDisponibles(deudaId)
        );
    }

    @PostMapping
    public ResponseEntity<GestionResponse> create(
            @Valid @RequestBody GestionRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(gestionService.createGestion(request));
    }
}