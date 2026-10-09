package com.intendencia.gestion_morosidad_api.modules.padron.controller;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.service.PadronService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/padrones")
@RequiredArgsConstructor
public class PadronController {

    private final PadronService padronService;

    /** El número de padrón se repite entre localidades: devuelve todos (filtrables por ?localidad=). */
    @GetMapping("/{numeroPadron}")
    public ResponseEntity<List<PadronResponse>> buscarPorNumeroPadron(
            @PathVariable String numeroPadron,
            @RequestParam(required = false) String localidad) {

        List<PadronResponse> padrones = padronService.buscarPorNumeroPadron(numeroPadron, localidad);
        return padrones.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(padrones);
    }

    @GetMapping("/{numeroPadron}/contribuyente")
    public ResponseEntity<List<ContribuyenteResponse>> buscarContribuyentes(
            @PathVariable String numeroPadron,
            @RequestParam(required = false) String localidad) {

        List<ContribuyenteResponse> contribuyentes = padronService.buscarContribuyentes(numeroPadron, localidad);
        return contribuyentes.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(contribuyentes);
    }
}