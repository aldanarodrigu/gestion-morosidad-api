package com.intendencia.gestion_morosidad_api.integration;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaPaginador;
import com.intendencia.gestion_morosidad_api.integration.intendencia.config.IntendenciaApiConfig;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopat.client.GeoPatClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Reproduce el volumen y la pagina del fallo real sin consultar la API ni escribir en la base. */
class GeoPatDescargaCompletaTest {
    @ParameterizedTest
    @ValueSource(strings = {"sinError", "504Transitorio", "504Persistente"})
    void descargaMasDeCincuentaMilSinCambiarFiltrosNiDuplicarPaginas(String escenario) {
        var configurado = new IntendenciaApiConfig().intendenciaRestClient(RestClient.builder(),
                "https://api.example.test/utec_geopagos_service", "usuario-prueba", "clave-prueba",
                Duration.ofSeconds(10), Duration.ofSeconds(180));
        var builder = configurado.mutate();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GeoPatClient(new IntendenciaPaginador(builder.build(), 1000));
        var auth = new HttpHeaders();
        auth.setBasicAuth("usuario-prueba", "clave-prueba");
        boolean persistente = escenario.equals("504Persistente");

        for (int pagina = 1; pagina <= (persistente ? 51 : 52); pagina++) {
            int intentosFallidos = pagina != 51 || escenario.equals("sinError") ? 0 : persistente ? 3 : 1;
            int exitos = pagina == 51 && persistente ? 0 : 1;
            for (int intento = 0; intento < intentosFallidos + exitos; intento++) {
                int actual = pagina;
                var expectativa = server.expect(request -> {
                    assertThat(request.getURI().getScheme()).isEqualTo("https");
                    assertThat(request.getURI().getHost()).isEqualTo("api.example.test");
                    assertThat(request.getURI().getPath()).isEqualTo(
                            "/utec_geopagos_service/api/v1/utec/sucive/facturas/pendientes");
                    var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
                    assertThat(query).hasSize(5);
                    assertThat(query.get("fecha_inicio")).containsExactly("2013-01-01");
                    assertThat(query.get("fecha_corte")).containsExactly("2026-11-12");
                    assertThat(query.get("include_total")).containsExactly("0");
                    assertThat(query.get("page")).containsExactly(Integer.toString(actual));
                    assertThat(query.get("per_page")).containsExactly("1000");
                }).andExpect(header(HttpHeaders.AUTHORIZATION, auth.getFirst(HttpHeaders.AUTHORIZATION)))
                  .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE));
                if (intento < intentosFallidos) {
                    expectativa.andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
                            .contentType(MediaType.TEXT_HTML).body("<html>504 Gateway Time-out</html>"));
                } else {
                    expectativa.andRespond(withSuccess(pagina(actual), MediaType.APPLICATION_JSON));
                }
            }
        }

        if (persistente) {
            assertThatThrownBy(() -> client.obtenerFacturasPendientes(
                    LocalDate.of(2013, 1, 1), LocalDate.of(2026, 11, 12)))
                    .isInstanceOfSatisfying(RestClientResponseException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
        } else {
            var resultado = client.obtenerFacturasPendientes(LocalDate.of(2013, 1, 1), LocalDate.of(2026, 11, 12));
            assertThat(resultado.getData()).hasSize(51_003);
            assertThat(resultado.getData()).extracting(fila -> Integer.parseInt(fila.cm().toString()))
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, 51_003).boxed().toList());
            assertThat(resultado.getMeta().getTotal()).isEqualTo(51_003);
        }
        server.verify();
    }

    private static String pagina(int numero) {
        int desde = (numero - 1) * 1000 + 1;
        int hasta = Math.min(numero * 1000, 51_003);
        String filas = IntStream.rangeClosed(desde, hasta)
                .mapToObj(cm -> "{\"CM\":" + cm + "}").collect(Collectors.joining(","));
        return "{\"data\":[" + filas + "],\"meta\":{\"total\":null,\"last_page\":null,"
                + "\"current_page\":" + numero + ",\"per_page\":1000,\"from\":" + desde
                + ",\"to\":" + hasta + ",\"has_more\":" + (hasta < 51_003) + "}}";
    }
}
