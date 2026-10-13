package com.intendencia.gestion_morosidad_api.modules.tributo.controller;

import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoResponse;
import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import com.intendencia.gestion_morosidad_api.modules.tributo.service.TributoService;
import java.util.List;
import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tributos")
@RequiredArgsConstructor
public class TributoController {

    private final TributoService tributoService;

    /** Catálogo completo (pocos registros: sin paginación). Sirve, por ejemplo, para llenar el filtro por tributo. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public List<TributoResponse> listar() {
        return tributoService.listar();
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public TributoResponse actualizarDescripcion(@PathVariable Long id,
            @Valid @RequestBody TributoUpdateRequest request) {
        return tributoService.actualizarDescripcion(id, request);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TributoResponse> crear(@Valid @RequestBody TributoRequest request) {
        return ResponseEntity.status(201).body(tributoService.crear(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        tributoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

}
