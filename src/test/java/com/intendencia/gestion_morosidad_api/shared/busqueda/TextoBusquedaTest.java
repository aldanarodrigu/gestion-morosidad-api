package com.intendencia.gestion_morosidad_api.shared.busqueda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextoBusquedaTest {

    @Test
    void normalizarQuitaTildesYMayusculas() {
        assertThat(TextoBusqueda.normalizar("  Núñez GARCÍA Lucía ")).isEqualTo("nunez garcia lucia");
    }

    @Test
    void contieneEscapaComodines() {
        assertThat(TextoBusqueda.contiene("100%_a\\b")).isEqualTo("%100\\%\\_a\\\\b%");
    }

    @Test
    void laTablaDeTranslateTieneElMismoLargoYCoincideConNormalizar() {
        assertThat(TextoBusqueda.CON_TILDE).hasSameSizeAs(TextoBusqueda.SIN_TILDE);
        assertThat(TextoBusqueda.normalizar(TextoBusqueda.CON_TILDE)).isEqualTo(TextoBusqueda.SIN_TILDE);
    }
}
