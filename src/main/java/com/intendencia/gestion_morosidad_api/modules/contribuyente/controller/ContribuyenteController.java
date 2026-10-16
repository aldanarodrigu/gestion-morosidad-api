package com.intendencia.gestion_morosidad_api.modules.contribuyente.controller;

import com.intendencia.gestion_morosidad_api.modules.contacto.dto.ContactoRequest;
import com.intendencia.gestion_morosidad_api.modules.contacto.dto.ContactoResponse;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.ContactoService;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.service.ContribuyenteService;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.DeudaService;
import com.intendencia.gestion_morosidad_api.modules.deuda.dto.DeudaResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;

@RestController
@RequestMapping("/api/contribuyentes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
public class ContribuyenteController {

    private final ContribuyenteService contribuyenteService;
    private final ContactoService contactoService;
    private final DeudaService deudaService;

    /** Paginado: ?page=0&size=20&sort=contribuyente.nombre,asc (por defecto ordena por nombre). */
    @GetMapping
    public PaginaResponse<ContribuyenteResponse> listarContribuyentes(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String documento,
            @PageableDefault(size = 20, sort = "contribuyente.nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return contribuyenteService.listarContribuyentes(nombre, documento, pageable);
    }

    @GetMapping("/{cm}")
    public ResponseEntity<ContribuyenteResponse> buscarPorCm(
            @PathVariable String cm, @RequestParam(defaultValue = "INTENDENCIA") OrigenDatos origen) {

        return (origen == OrigenDatos.INTENDENCIA ? contribuyenteService.buscarPorCm(cm) : contribuyenteService.buscarPorCm(cm, origen))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{cm}/padrones")
    public ResponseEntity<List<PadronResponse>> listarPadrones(@PathVariable String cm, @RequestParam(defaultValue = "INTENDENCIA") OrigenDatos origen) {
        return (origen == OrigenDatos.INTENDENCIA ? contribuyenteService.listarPadronesPorCm(cm) : contribuyenteService.listarPadronesPorCm(cm, origen))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{cm}/contactos")
    public List<ContactoResponse> listarContactos(
            @PathVariable String cm, @RequestParam(defaultValue = "INTENDENCIA") OrigenDatos origen
    ) {
        return origen == OrigenDatos.INTENDENCIA ? contactoService.listarPorCm(cm) : contactoService.listarPorCm(cm, origen);
    }

    @PutMapping("/{cm}/contactos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public ContactoResponse guardarOActualizarContacto(
            @PathVariable String cm, @RequestParam(defaultValue = "INTENDENCIA") OrigenDatos origen,
            @Valid @RequestBody ContactoRequest request
    ) {
        return origen == OrigenDatos.INTENDENCIA ? contactoService.guardarOActualizar(cm, request) : contactoService.guardarOActualizar(cm, request, origen);
    }

    @GetMapping("/id/{contribuyenteId}/deudas")
    public List<DeudaResponse> listarDeudasPorContribuyenteId(
            @PathVariable Long contribuyenteId
    ) {
        return deudaService.listarPorContribuyenteId(contribuyenteId);
    }
}
