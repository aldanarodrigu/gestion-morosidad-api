package com.intendencia.gestion_morosidad_api.modules.convenio;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/convenios")
@RequiredArgsConstructor
public class ConvenioController {
    private final ConvenioService convenios;
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public IntendenciaApiResponse<ConvenioResponse> consultar(
            @RequestParam(defaultValue="INTENDENCIA") OrigenDatos origen,
            @RequestParam(defaultValue="1") int pagina, @RequestParam(defaultValue="20") int tamanio,
            @RequestParam(required=false) Long cm, @RequestParam(required=false) String documento,
            @RequestParam(required=false) String nombre, @RequestParam(required=false) LocalDate desde,
            @RequestParam(required=false) LocalDate hasta) {
        return convenios.consultar(origen, pagina, tamanio, cm, documento, nombre, desde, hasta);
    }
}
