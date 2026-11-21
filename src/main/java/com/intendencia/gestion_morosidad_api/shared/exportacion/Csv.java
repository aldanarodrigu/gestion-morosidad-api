package com.intendencia.gestion_morosidad_api.shared.exportacion;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Csv {
    private static final Pattern FORMULA_CSV = Pattern.compile("^[\\s\\p{Z}\\p{Cc}]*[=+@-]");

    private Csv() {}

    public static String linea(Object... valores) {
        return Arrays.stream(valores).map(valor -> {
            String texto = valor == null ? "" : valor.toString();
            if (valor instanceof String && FORMULA_CSV.matcher(texto).find()) texto = "'" + texto;
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }).collect(Collectors.joining(";", "", "\r\n"));
    }
}
