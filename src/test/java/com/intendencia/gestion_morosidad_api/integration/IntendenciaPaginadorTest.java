package com.intendencia.gestion_morosidad_api.integration;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.*;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IntendenciaPaginadorTest {
    MockRestServiceServer server;
    IntendenciaPaginador paginador;
    @BeforeEach void preparar() {
        var builder = RestClient.builder().baseUrl("https://api.example.test");
        server = MockRestServiceServer.bindTo(builder).build();
        paginador = new IntendenciaPaginador(builder.build(), 1000);
    }
    IntendenciaApiResponse<GeoPagosFacturaPendienteDto> descargar() {
        return paginador.obtenerTodo("/pendientes", Map.of("fecha_desde", "2026-10-01"),
                new ParameterizedTypeReference<>() {}, GeoPagosFacturaPendienteDto::cm);
    }
    void pagina(int numero, String json) {
        server.expect(requestTo("https://api.example.test/pendientes?fecha_desde=2026-10-01&page=" + numero + "&per_page=1000"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
    String primera() {
        return """
                {"data":[{"CM":1},{"CM":2}],"meta":{"total":3,"current_page":1,
                "per_page":2,"last_page":2,"from":1,"to":2,"has_more":true}}
                """;
    }
    String segunda() {
        return """
                {"data":[{"CM":3}],"meta":{"total":3,"current_page":2,
                "per_page":2,"last_page":2,"from":3,"to":3,"has_more":false}}
                """;
    }
    @Test void reunePaginasAunqueElServidorReduzcaElTamanio() {
        pagina(1, primera()); pagina(2, segunda());
        var resultado = descargar();
        assertThat(resultado.getData()).extracting(fila -> fila.cm().toString()).containsExactly("1", "2", "3");
        assertThat(resultado.getMeta().getTotal()).isEqualTo(3);
        server.verify();
    }
    @Test void aceptaConsultaCompletaSinResultados() {
        pagina(1, """
                {"data":[],"meta":{"total":0,"current_page":1,"per_page":1000,
                "last_page":1,"from":null,"to":null,"has_more":false}}
                """);
        assertThat(descargar().getData()).isEmpty(); server.verify();
    }
    @ParameterizedTest
    @ValueSource(strings = {"duplicada", "total", "pagina", "tamano", "ultima", "continuacion", "limites", "incompleta", "sinId"})
    void rechazaUnaSegundaPaginaInconsistenteSinEntregarLaPrimera(String caso) {
        String segunda = switch (caso) {
            case "duplicada" -> segunda().replace("\"CM\":3", "\"CM\":2");
            case "total" -> segunda().replace("\"total\":3", "\"total\":4");
            case "pagina" -> segunda().replace("\"current_page\":2", "\"current_page\":1");
            case "tamano" -> segunda().replace("\"per_page\":2", "\"per_page\":3");
            case "ultima" -> segunda().replace("\"last_page\":2", "\"last_page\":3");
            case "continuacion" -> segunda().replace("\"has_more\":false", "\"has_more\":true");
            case "limites" -> segunda().replace("\"from\":3", "\"from\":2");
            case "incompleta" -> segunda().replace("[{\"CM\":3}]", "[]");
            case "sinId" -> segunda().replace("\"CM\":3", "\"CM\":null");
            default -> throw new IllegalArgumentException();
        };
        pagina(1, primera()); pagina(2, segunda);
        assertThatThrownBy(this::descargar).isInstanceOf(IntegracionExternaException.class).hasMessageContaining("pagina 2");
        server.verify();
    }
    @Test void timeoutEnSegundaPaginaNoDevuelveResultadosParciales() {
        pagina(1, primera());
        server.expect(queryParam("page", "2")).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        assertThatThrownBy(this::descargar).isInstanceOf(RestClientResponseException.class);
        server.verify();
    }
    @Test void rechazaFormatoSinMetadatosDePaginacion() {
        pagina(1, "{\"data\":[{\"CM\":1}],\"meta\":{\"total\":1}}");
        assertThatThrownBy(this::descargar).isInstanceOf(IntegracionExternaException.class);
        server.verify();
    }
}
