package com.intendencia.gestion_morosidad_api.integration;

import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaCanceladaDto;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;
import static org.assertj.core.api.Assertions.*;

class FechaIntendenciaDeserializerTest {
    final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest @ValueSource(strings = {"\"0000-00-00\"", "null", "\"\""})
    void fechasNoInformadasNoImpidenLeerElImporte(String fecha) {
        var fila = mapper.readValue("{\"IMPORTE_DEUDA\":50,\"DEUDA_DESDE\":" + fecha
                + ",\"ULTIMO_VENCIMIENTO\":" + fecha + "}", GeoPagosFacturaPendienteDto.class);
        assertThat(fila.importeDeuda()).isEqualByComparingTo("50");
        assertThat(fila.deudaDesde()).isNull();
        assertThat(fila.ultimoVencimiento()).isNull();
        var cobro = mapper.readValue("{\"FECHA_COBRO\":" + fecha + "}", GeoPagosFacturaCanceladaDto.class);
        assertThat(cobro.fechaCobro()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"2000-00-30", "2000-01-00", "0000-01-30"})
    void losVencimientosParcialesQuedanSinFecha(String fecha) {
        var fila = mapper.readValue("""
                {"DEUDA_DESDE":"%s","ULTIMO_VENCIMIENTO":"%s","IMPORTE_DEUDA":50}
                """.formatted(fecha, fecha), GeoPagosFacturaPendienteDto.class);
        assertThat(fila.deudaDesde()).isNull();
        assertThat(fila.ultimoVencimiento()).isNull();
        assertThat(fila.importeDeuda()).isEqualByComparingTo("50");
    }

    @ParameterizedTest @ValueSource(strings = {"2000-00-30", "2000-01-00"})
    void lasFechasDeCobroConMesODiaCeroSeSiguenRechazando(String fecha) {
        assertThatThrownBy(() -> mapper.readValue("""
                {"FECHA_COBRO":"%s"}
                """.formatted(fecha), GeoPagosFacturaCanceladaDto.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test void conservaLaFechaReal() {
        var fila = mapper.readValue("{\"DEUDA_DESDE\":\"2025-08-15\"}", GeoPagosFacturaPendienteDto.class);
        assertThat(fila.deudaDesde()).isEqualTo(LocalDate.of(2025,8,15));
    }

    @ParameterizedTest @ValueSource(strings = {"ayer", "2025-13-01", "2025-02-31", "2025-00-99", "2025-13-00"})
    void otrosValoresInvalidosSiguenAbortandoLaCarga(String fecha) {
        assertThatThrownBy(() -> mapper.readValue("{\"DEUDA_DESDE\":\"" + fecha + "\"}", GeoPagosFacturaPendienteDto.class))
                .isInstanceOf(JacksonException.class);
    }
}
