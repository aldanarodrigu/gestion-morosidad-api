package com.intendencia.gestion_morosidad_api.deuda.repository;

import com.intendencia.gestion_morosidad_api.deuda.dto.DeudaFiltro;
import com.intendencia.gestion_morosidad_api.deuda.entity.Deuda;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Filtros dinámicos del listado de deudas. Todo se arma con Criteria API (parámetros, sin concatenar SQL). */
public final class DeudaSpecifications {

    private static final char ESCAPE = '\\';

    private DeudaSpecifications() {
    }

    public static Specification<Deuda> conFiltro(DeudaFiltro filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();

            if (filtro.estado() != null) {
                predicados.add(cb.equal(root.get("estado"), filtro.estado()));
            }
            if (tieneTexto(filtro.padron())) {
                predicados.add(cb.equal(root.get("numeroPadron"), filtro.padron().trim()));
            }
            if (tieneTexto(filtro.contribuyente())) {
                String texto = filtro.contribuyente().trim();
                predicados.add(cb.or(
                        cb.like(cb.lower(root.get("contribuyenteNombre")), contiene(texto), ESCAPE),
                        cb.equal(root.get("contribuyenteDocumento"), texto)));
            }
            if (tieneTexto(filtro.localidad())) {
                predicados.add(cb.like(cb.lower(root.get("localidad")), contiene(filtro.localidad()), ESCAPE));
            }

            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** Patrón LIKE "contiene", en minúsculas y con comodines escapados. */
    private static String contiene(String texto) {
        return "%" + escaparLike(texto.trim().toLowerCase(Locale.ROOT)) + "%";
    }

    /** Evita que % y _ ingresados por el usuario actúen como comodines. */
    private static String escaparLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
