package com.intendencia.gestion_morosidad_api.modules.contribuyente.controller;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contribuyentes")
@RequiredArgsConstructor
public class ContribuyenteController {

    private final ContribuyenteService contribuyenteService;

    @GetMapping
    public List<ContribuyenteResponse> listarContribuyentes() {
        return contribuyenteService.listarContribuyentes();
    }

    @GetMapping("/{cm}")
    public ResponseEntity<ContribuyenteResponse> buscarPorCm(
            @PathVariable String cm) {

        return contribuyenteService.buscarPorCm(cm)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}