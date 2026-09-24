package com.intendencia.gestion_morosidad_api.modules.tributo.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodigosTributoTest {

    @Test
    void separaPorComaYQuitaEspaciosVaciosYRepetidos() {
        assertThat(CodigosTributo.parsear(" 101, 3802,,101 , ")).containsExactly("101", "3802");
    }

    @Test
    void nuloOVacioDevuelveConjuntoVacio() {
        assertThat(CodigosTributo.parsear(null)).isEmpty();
        assertThat(CodigosTributo.parsear("  ")).isEmpty();
    }
}
