package com.intendencia.gestion_morosidad_api.modules.tributo.controller;

import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoResponse;
import com.intendencia.gestion_morosidad_api.modules.tributo.service.TributoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tributos")
@RequiredArgsConstructor
public class TributoController {

    private final TributoService tributoService;

    /** Catálogo completo (pocos registros: sin paginación). Sirve, por ejemplo, para llenar el filtro por tributo. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')")
    public List<TributoResponse> listar() {
        return tributoService.listar();
    }
}
