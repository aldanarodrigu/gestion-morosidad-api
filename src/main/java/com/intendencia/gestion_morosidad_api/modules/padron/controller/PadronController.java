package com.intendencia.gestion_morosidad_api.modules.padron.controller;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.service.PadronService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/padrones")
@RequiredArgsConstructor
public class PadronController {

    private final PadronService padronService;

    @GetMapping("/{numeroPadron}")
    public ResponseEntity<PadronResponse> buscarPorNumeroPadron(
            @PathVariable String numeroPadron) {

        return padronService.buscarPorNumeroPadron(numeroPadron)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{numeroPadron}/contribuyente")
    public ResponseEntity<ContribuyenteResponse> buscarContribuyente(
            @PathVariable String numeroPadron) {

        return padronService.buscarContribuyente(numeroPadron)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}