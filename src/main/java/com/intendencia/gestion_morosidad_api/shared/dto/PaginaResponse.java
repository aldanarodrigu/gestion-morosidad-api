package com.intendencia.gestion_morosidad_api.shared.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Formato estable para respuestas paginadas (no expone la estructura interna de Spring Data). */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamanio,
        long totalElementos,
        int totalPaginas) {

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
