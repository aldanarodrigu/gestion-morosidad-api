package com.intendencia.gestion_morosidad_api.integration;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaPaginador;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeoPagosPaginacionTest {
    @ParameterizedTest @ValueSource(strings = {"contribuyentes-geopagos", "facturas/pendientes", "facturas/canceladas"})
    void descargaTodasLasPaginasConservandoLasFechasDeCobros(String ruta) {
        var builder = RestClient.builder().baseUrl("https://api.example.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GeoPagosClient(new IntendenciaPaginador(builder.build(), 1000));
        for (int pagina = 1; pagina <= 2; pagina++) {
            var expectativa = server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/api/v1/utec/" + ruta))
                    .andExpect(queryParam("page", String.valueOf(pagina))).andExpect(queryParam("per_page", "1000"));
            if (ruta.endsWith("canceladas")) expectativa.andExpect(queryParam("fecha_desde", "2026-10-01"))
                    .andExpect(queryParam("fecha_hasta", "2026-10-21"));
            // Cobros puede repetir CM; contribuyentes y pendientes son unicos por CM.
            int cm = ruta.endsWith("canceladas") ? 1 : pagina;
            expectativa.andRespond(withSuccess("""
                    {"data":[{"CM":%d}],"meta":{"total":2,"current_page":%d,
                    "per_page":1,"last_page":2,"from":%d,"to":%d,"has_more":%s}}
                    """.formatted(cm, pagina, pagina, pagina, pagina == 1), MediaType.APPLICATION_JSON));
        }
        var resultado = switch (ruta) {
            case "contribuyentes-geopagos" -> client.obtenerContribuyentes();
            case "facturas/pendientes" -> client.obtenerFacturasPendientes();
            default -> client.obtenerFacturasCanceladas(LocalDate.of(2026,10,1), LocalDate.of(2026,10,21));
        };
        assertThat(resultado.getData()).hasSize(2);
        server.verify();
    }
}
