package com.intendencia.gestion_morosidad_api.shared.busqueda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextoBusquedaTest {

    @Test
    void normalizarQuitaTildesYMayusculas() {
        assertThat(TextoBusqueda.normalizar("  Núñez GARCÍA Lucía ")).isEqualTo("nunez garcia lucia");
    }

    @Test
    void contieneEscapaComodines() {
        assertThat(TextoBusqueda.contiene("100%_a\\b")).isEqualTo("%100\\%\\_a\\\\b%");
    }

    @ParameterizedTest
    @ValueSource(strings = {"  Zoë Mārtin ", "ZOË MĀRTIN", "Zoe\u0308 Ma\u0304rtin"})
    void convierteIgualLosAcentosCompuestosYCombinados(String nombre) {
        assertThat(TextoBusqueda.normalizar(nombre)).isEqualTo("zoe martin");
    }
}
