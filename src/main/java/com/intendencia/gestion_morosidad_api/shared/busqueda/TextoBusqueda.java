package com.intendencia.gestion_morosidad_api.shared.busqueda;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalización de texto para las búsquedas de cartera. */
public final class TextoBusqueda {

    // Bloque de marcas combinadas que usa la descomposición de los acentos latinos.
    // La misma expresión se aplica en Java y en PostgreSQL.
    public static final String MARCAS_DIACRITICAS = "[\u0300-\u036F]+";

    private static final Pattern MARCAS = Pattern.compile(MARCAS_DIACRITICAS);

    private TextoBusqueda() {
    }

    public static String normalizar(String texto) {
        String descompuesto = Normalizer.normalize(texto.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return MARCAS.matcher(descompuesto).replaceAll("");
    }

    public static Expression<String> normalizar(CriteriaBuilder cb, Expression<String> columna) {
        Expression<String> descompuesta = cb.function("pg_catalog.normalize", String.class,
                cb.lower(columna), cb.literal(Normalizer.Form.NFD.name()));
        return cb.function("regexp_replace", String.class, descompuesta,
                cb.literal(MARCAS_DIACRITICAS), cb.literal(""), cb.literal("g"));
    }

    public static String contiene(String texto) {
        String escapado = normalizar(texto).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escapado + "%";
    }
}
