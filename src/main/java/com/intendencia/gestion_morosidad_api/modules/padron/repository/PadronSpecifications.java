package com.intendencia.gestion_morosidad_api.modules.padron.repository;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Filtros del listado de contribuyentes (un registro por padrón). Criteria API: sin concatenar SQL. */
public final class PadronSpecifications {

    private static final char ESCAPE = '\\';

    private PadronSpecifications() {
    }

    /**
     * Padrones con deuda vigente (no cancelada), filtrados por nombre o documento del contribuyente.
     *
     * @param nombre    texto contenido en el nombre, sin distinguir mayúsculas (opcional)
     * @param documento texto contenido en el documento (opcional; excluye contribuyentes sin documento)
     */
    public static Specification<Padron> conDeudaVigente(String nombre, String documento) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();

            Subquery<Long> deudaVigente = query.subquery(Long.class);
            Root<Deuda> deuda = deudaVigente.from(Deuda.class);
            deudaVigente.select(cb.literal(1L)).where(
                    cb.equal(deuda.get("padron"), root),
                    cb.notEqual(deuda.get("estado"), EstadoDeuda.CANCELADA));
            predicados.add(cb.exists(deudaVigente));

            Path<Contribuyente> contribuyente = root.get("contribuyente");
            if (tieneTexto(nombre)) {
                predicados.add(cb.like(cb.lower(contribuyente.get("nombre")), contiene(nombre), ESCAPE));
            }
            if (tieneTexto(documento)) {
                predicados.add(cb.like(cb.lower(contribuyente.get("documento")), contiene(documento), ESCAPE));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** Patrón LIKE "contiene", en minúsculas y con % y _ escapados. */
    private static String contiene(String texto) {
        String escapado = texto.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escapado + "%";
    }
}
