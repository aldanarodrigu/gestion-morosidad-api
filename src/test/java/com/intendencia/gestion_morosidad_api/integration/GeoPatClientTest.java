package com.intendencia.gestion_morosidad_api.integration;

import com.intendencia.gestion_morosidad_api.integration.intendencia.geopat.client.GeoPatClient;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeoPatClientTest {
    private MockRestServiceServer server;
    private GeoPatClient client;

    @BeforeEach void preparar() {
        var builder = RestClient.builder().baseUrl("https://api.example.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeoPatClient(builder.build());
    }

    @Test void pendientesConservaTributosYPermitePersonaNula() {
        server.expect(requestTo("https://api.example.test/api/v1/utec/geopat/facturas/pendientes"))
                .andRespond(withSuccess("""
                        {"data":[{"CM":123,"NUMERO_PADRON":"004567","PADTIPO":"PAT",
                        "PERSONA":null,"DOCUMENTO":null,"TRIBUTOS_DEUDA":"1, 2",
                        "IMPORTE_DEUDA":"1550.00","DEUDA_DESDE":"2025-08-15"}],"meta":{"total":1}}
                        """, MediaType.APPLICATION_JSON));
        var fila = client.obtenerFacturasPendientes().getData().getFirst();
        assertThat(fila.persona()).isNull();
        assertThat(fila.numeroPadron()).isEqualTo("004567");
        assertThat(fila.tributosDeuda()).isEqualTo("1, 2");
        assertThat(fila.importeDeuda()).isEqualByComparingTo("1550.00");
        server.verify();
    }

    @Test void canceladasUsaIntervaloDeFechasDelAnexo() {
        server.expect(requestTo("https://api.example.test/api/v1/utec/geopat/facturas/canceladas?fecha_desde=2026-03-22&fecha_hasta=2026-03-29"))
                .andRespond(withSuccess("{\"data\":[],\"meta\":{\"total\":0}}", MediaType.APPLICATION_JSON));
        assertThat(client.obtenerFacturasCanceladas(LocalDate.of(2026,3,22), LocalDate.of(2026,3,29)).getData()).isEmpty();
        server.verify();
    }

    @Test void contribuyentesNoInventaContactosDeGeoPagos() {
        server.expect(requestTo("https://api.example.test/api/v1/utec/geopat/contribuyentes"))
                .andRespond(withSuccess("""
                        {"data":[{"CM":123,"NUMERO_PADRON":4567,"TIPO_PADRON":"PAT",
                        "NOMBRE_PERSONAS":null,"TELEFONO_PERSONAS":"099000111","EMAIL_PERSONAS":null}]}
                        """, MediaType.APPLICATION_JSON));
        var fila = client.obtenerContribuyentes().getData().getFirst();
        assertThat(fila.tipoPadron()).isEqualTo("PAT");
        assertThat(fila.nombrePersonas()).isNull();
        assertThat(fila.telefonoPersonas()).isEqualTo("099000111");
        assertThat(fila.telefonoGeoPagos()).isNull();
        server.verify();
    }
}
