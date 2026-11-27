package com.intendencia.gestion_morosidad_api.shared.busqueda;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

// los nombres vienen de GeoPagos sin tildes (Nunez, Lucia), asi que se busca sin tildes
public final class TextoBusqueda {

    // para translate(), tienen que tener el mismo largo
    public static final String CON_TILDE = "áàäâãéèëêíìïîóòöôõúùüûñç";
    public static final String SIN_TILDE = "aaaaaeeeeiiiiooooouuuunc";

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");

    private TextoBusqueda() {
    }

    public static String normalizar(String texto) {
        String descompuesto = Normalizer.normalize(texto.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return MARCAS.matcher(descompuesto).replaceAll("");
    }

    public static Expression<String> normalizar(CriteriaBuilder cb, Expression<String> columna) {
        return cb.function("translate", String.class, cb.lower(columna),
                cb.literal(CON_TILDE), cb.literal(SIN_TILDE));
    }

    public static String contiene(String texto) {
        String escapado = normalizar(texto).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escapado + "%";
    }
}
