package com.intendencia.gestion_morosidad_api.modules.accion;

import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionRequest;
import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionResponse;
import com.intendencia.gestion_morosidad_api.modules.accion.dto.AccionUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/acciones")
public class AccionController {

    private final AccionService accionService;

    public AccionController(AccionService accionService) {
        this.accionService = accionService;
    }

    // GET /api/acciones
    @GetMapping
    public ResponseEntity<List<AccionResponse>> getAll() {
        return ResponseEntity.ok(accionService.getAllAcciones());
    }

    // GET /api/acciones/{id}
    @GetMapping("/{id}")
    public ResponseEntity<AccionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(accionService.getAccionById(id));
    }

    // POST /api/acciones
    @PostMapping
    public ResponseEntity<AccionResponse> create(
            @Valid @RequestBody AccionRequest accionRequest) {

        return ResponseEntity.ok(
                accionService.createAccion(accionRequest)
        );
    }

    // PUT /api/acciones/{id}
    @PutMapping("/{id}")
    public ResponseEntity<AccionResponse> updateEstado(
            @PathVariable Long id,
            @Valid @RequestBody AccionUpdateRequest request) {

        return ResponseEntity.ok(
                accionService.updateEstado(id, request)
        );
    }

    // DELETE /api/acciones/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {

        accionService.deleteAccion(id);

        return ResponseEntity.noContent().build();
    }
}