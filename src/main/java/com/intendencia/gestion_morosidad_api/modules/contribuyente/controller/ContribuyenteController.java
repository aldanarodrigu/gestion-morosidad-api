package com.intendencia.gestion_morosidad_api.modules.contribuyente.controller;

import com.intendencia.gestion_morosidad_api.modules.contacto.dto.ContactoRequest;
import com.intendencia.gestion_morosidad_api.modules.contacto.dto.ContactoResponse;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.ContactoService;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contribuyentes")
@RequiredArgsConstructor
public class ContribuyenteController {

    private final ContribuyenteService contribuyenteService;
    private final ContactoService contactoService;

    @GetMapping
    public List<ContribuyenteResponse> listarContribuyentes(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String documento) {
        return contribuyenteService.listarContribuyentes(nombre, documento);
    }

    @GetMapping("/{cm}")
    public ResponseEntity<ContribuyenteResponse> buscarPorCm(
            @PathVariable String cm) {

        return contribuyenteService.buscarPorCm(cm)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{cm}/padrones")
    public ResponseEntity<List<PadronResponse>> listarPadrones(@PathVariable String cm) {
        return contribuyenteService.listarPadronesPorCm(cm)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{cm}/contactos")
    public List<ContactoResponse> listarContactos(
            @PathVariable String cm
    ) {
        return contactoService.listarPorCm(cm);
    }

    @PutMapping("/{cm}/contactos")
    public ContactoResponse guardarOActualizarContacto(
            @PathVariable String cm,
            @Valid @RequestBody ContactoRequest request
    ) {
        return contactoService.guardarOActualizar(cm, request);
    }
}
