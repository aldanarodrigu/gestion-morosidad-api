package com.intendencia.gestion_morosidad_api.deuda.controller;

import com.intendencia.gestion_morosidad_api.deuda.dto.ActualizarEstadoRequest;
import com.intendencia.gestion_morosidad_api.deuda.dto.DeudaFiltro;
import com.intendencia.gestion_morosidad_api.deuda.dto.DeudaResponse;
import com.intendencia.gestion_morosidad_api.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.deuda.service.DeudaService;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DeudaController {

    private final DeudaService deudaService;

    /**
     * Listado paginado. Por defecto ordena por antigüedad (deudaDesde ascendente = más atrasadas primero).
     * Ejemplo: /api/deudas?estado=PENDIENTE&contribuyente=perez&localidad=libertad&sort=importe,desc&page=0&size=20
     */
    @GetMapping("/deudas")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public PaginaResponse<DeudaResponse> listar(
            @RequestParam(required = false) EstadoDeuda estado,
            @RequestParam(required = false) String padron,
            @RequestParam(required = false) String contribuyente,
            @RequestParam(required = false) String localidad,
            @PageableDefault(size = 20, sort = "deudaDesde", direction = Sort.Direction.ASC) Pageable pageable) {
        return deudaService.listar(new DeudaFiltro(estado, padron, contribuyente, localidad), pageable);
    }

    @GetMapping("/deudas/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public DeudaResponse obtener(@PathVariable Long id) {
        return deudaService.obtener(id);
    }

    @GetMapping("/padrones/{numPadron}/deudas")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public List<DeudaResponse> listarPorPadron(@PathVariable String numPadron) {
        return deudaService.listarPorPadron(numPadron);
    }

    @PutMapping("/deudas/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public DeudaResponse actualizarEstado(@PathVariable Long id, @Valid @RequestBody ActualizarEstadoRequest request) {
        return deudaService.actualizarEstado(id, request.estado());
    }
}
