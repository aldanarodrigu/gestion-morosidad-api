package com.intendencia.gestion_morosidad_api.modules.deuda.controller;

import com.intendencia.gestion_morosidad_api.modules.deuda.dto.DeudaFiltro;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.CarteraExportacionService;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
    public enum Formato { XLSX, CSV }

    @GetMapping("/exportar")
    @PreAuthorize("hasRole('ANALISTA')")
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "false") boolean agrupar,
            @RequestParam(defaultValue = "XLSX") Formato formato,
            @PageableDefault(sort = "deudaDesde") Pageable pageable) throws IOException {
        var filtro = new DeudaFiltro(estado, padron, contribuyente, localidad, segmento, tributo,
                conContacto, tipoPadron, origen, desde, hasta);
        boolean csv = formato == Formato.CSV;
        byte[] archivo = csv ? exportacionService.exportarCsv(filtro, pageable.getSort(), agrupar)
                : exportacionService.exportar(filtro, pageable.getSort(), agrupar);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(csv ? "text/csv;charset=UTF-8"
                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cartera-morosidad." + (csv ? "csv" : "xlsx"))
                .cacheControl(CacheControl.noStore())
                .body(archivo);
    }
}
