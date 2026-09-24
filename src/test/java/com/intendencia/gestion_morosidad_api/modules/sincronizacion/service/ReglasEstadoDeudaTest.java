package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReglasEstadoDeudaTest {

    @ParameterizedTest(name = "{0} con convenio={1} -> {2}")
    @CsvSource({
            "PENDIENTE,   true,  EN_CONVENIO",
            "EN_GESTION,  true,  EN_CONVENIO",
            "PENDIENTE,   false, PENDIENTE",
            "EN_GESTION,  false, EN_GESTION",
            "CANCELADA,   false, PENDIENTE",
            "EN_CONVENIO, false, PENDIENTE"})
    void estadoDeUnaDeudaQueSigueEnPendientes(EstadoDeuda actual, boolean convenio, EstadoDeuda esperado) {
        assertThat(ReglasEstadoDeuda.enPendientes(actual, convenio)).isEqualTo(esperado);
    }
}
