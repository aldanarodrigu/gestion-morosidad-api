package com.intendencia.gestion_morosidad_api.modules.deuda.controller;

import com.intendencia.gestion_morosidad_api.modules.deuda.dto.DeudaFiltro;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.CarteraExportacionService;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deudas")
@RequiredArgsConstructor
public class CarteraExportacionController {
    private final CarteraExportacionService exportacionService;

    @GetMapping("/exportar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) EstadoDeuda estado,
            @RequestParam(required = false) String padron,
            @RequestParam(required = false) String contribuyente,
            @RequestParam(required = false) String localidad,
            @RequestParam(required = false) Long segmento,
            @RequestParam(required = false) String tributo,
            @RequestParam(required = false) Boolean conContacto,
            @RequestParam(required = false) String tipoPadron,
            @RequestParam(required = false) OrigenDatos origen,
            @RequestParam(defaultValue = "false") boolean agrupar,
            @PageableDefault(sort = "deudaDesde") Pageable pageable) throws IOException {
        var filtro = new DeudaFiltro(estado, padron, contribuyente, localidad, segmento, tributo,
                conContacto, tipoPadron, origen);
        byte[] archivo = exportacionService.exportar(filtro, pageable.getSort(), agrupar);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cartera-morosidad.xlsx")
                .cacheControl(CacheControl.noStore())
                .body(archivo);
    }
}
