package com.intendencia.gestion_morosidad_api.modules.cancelacion;

import com.intendencia.gestion_morosidad_api.modules.deuda.dto.FacturasPadronResponse;
import com.intendencia.gestion_morosidad_api.modules.deuda.service.FacturasPadronService;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cancelaciones")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR','NOTIFICADOR','ANALISTA')")
public class CancelacionController {
    private final CancelacionRepository cancelaciones;
    private final FacturasPadronService facturas;
    private final CancelacionExportacionService exportacion;

    public enum Formato { XLSX, CSV }

    @GetMapping
    public PaginaResponse<Cancelacion> listar(
            @ModelAttribute FiltroCancelaciones filtro,
            @PageableDefault(size = 20) Pageable pagina) {
        return cancelaciones.listar(filtro, pagina);
    }

    @GetMapping("/motivos")
    @PreAuthorize("hasRole('ANALISTA')")
    public List<String> listarMotivos() {
        return cancelaciones.listarMotivos();
    }

    @GetMapping("/exportar")
    @PreAuthorize("hasRole('ANALISTA')")
    public ResponseEntity<byte[]> exportar(@ModelAttribute FiltroCancelaciones filtro,
            @RequestParam(defaultValue = "XLSX") Formato formato) throws IOException {
        boolean csv = formato == Formato.CSV;
        byte[] archivo = csv ? exportacion.csv(filtro) : exportacion.xlsx(filtro);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(csv ? "text/csv;charset=UTF-8"
                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cancelaciones." + (csv ? "csv" : "xlsx"))
                .cacheControl(CacheControl.noStore())
                .body(archivo);
    }

    @GetMapping("/{id}/facturas")
    public FacturasPadronResponse facturasCanceladas(@PathVariable long id) {
        var registro = cancelaciones.buscar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cancelación solicitada."));
        return facturas.consultarPorCm(registro.origen(), registro.cm(), "CANCELADA", null, false);
    }
}
