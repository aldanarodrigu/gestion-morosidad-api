package com.intendencia.gestion_morosidad_api.modules.tributo.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class CodigosTributo {

    private CodigosTributo() {
    }

    /**
     * Convierte TRIBUTOS_DEUDA de GeoPagos ("101, 3802": texto, no lista JSON; puede ser null)
     * en el conjunto de códigos, sin espacios, vacíos ni repetidos.
     */
    public static Set<String> parsear(String tributosDeuda) {
        if (tributosDeuda == null || tributosDeuda.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(tributosDeuda.split(","))
                .map(String::trim)
                .filter(codigo -> !codigo.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
