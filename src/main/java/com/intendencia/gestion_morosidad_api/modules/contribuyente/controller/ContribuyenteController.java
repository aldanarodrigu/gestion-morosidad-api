package com.intendencia.gestion_morosidad_api.modules.contribuyente.controller;

import com.intendencia.gestion_morosidad_api.modules.contacto.dto.ContactoRequest;
import com.intendencia.gestion_morosidad_api.modules.contacto.dto.ContactoResponse;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.ContactoService;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
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
