package com.intendencia.gestion_morosidad_api.modules.reporte;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/reportes/cartera")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR','NOTIFICADOR','ANALISTA')")
public class ReporteController {
    private final ReporteService service;
    @GetMapping public ReporteCartera consultar(@RequestParam(required=false) OrigenDatos origen,@RequestParam(required=false) String tipoPadron,@RequestParam(required=false) Long segmento) {return service.consultar(origen,tipoPadron,segmento);}
    @GetMapping("/exportar") public ResponseEntity<byte[]> exportar(@RequestParam(required=false) OrigenDatos origen,@RequestParam(required=false) String tipoPadron,@RequestParam(required=false) Long segmento) throws IOException {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=reporte-cartera.xlsx").cacheControl(CacheControl.noStore()).body(service.exportar(origen,tipoPadron,segmento));
    }
}
