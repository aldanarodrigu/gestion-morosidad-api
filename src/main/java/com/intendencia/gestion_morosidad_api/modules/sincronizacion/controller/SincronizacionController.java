package com.intendencia.gestion_morosidad_api.modules.sincronizacion.controller;

import com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.service.SincronizacionDeudasService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sincronizaciones")
@RequiredArgsConstructor
public class SincronizacionController {

    private final SincronizacionDeudasService sincronizacionDeudasService;

    /** Ejecución manual por el Administrador (CU 2.16). Puede tardar: consulta toda la deuda vencida. */
    @PostMapping("/deudas")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResultadoSincronizacion sincronizarDeudas() {
        return sincronizacionDeudasService.sincronizar();
    }
}
