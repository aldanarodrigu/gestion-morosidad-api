package com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaCanceladaDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class GeoPagosClient {

    private final RestClient intendenciaRestClient;

    public IntendenciaApiResponse<GeoPagosContribuyenteDto> obtenerContribuyentes() {

        return intendenciaRestClient
                .get()
                .uri("/api/v1/utec/contribuyentes-geopagos")
                .retrieve()
                .body(new ParameterizedTypeReference<
                        IntendenciaApiResponse<GeoPagosContribuyenteDto>>() {
                });
    }

    /** Deuda vencida antes de hoy, agrupada por CM. No recibe parámetros (manual, sección 6). */
    public IntendenciaApiResponse<GeoPagosFacturaPendienteDto> obtenerFacturasPendientes() {
        return intendenciaRestClient
                .get()
                .uri("/api/v1/utec/facturas/pendientes")
                .retrieve()
                .body(new ParameterizedTypeReference<
                        IntendenciaApiResponse<GeoPagosFacturaPendienteDto>>() {
                });
    }

    /**
     * Último cobro de cada CM en el intervalo [desde, hasta): hasta NO se incluye (manual, sección 5).
     */
    public IntendenciaApiResponse<GeoPagosFacturaCanceladaDto> obtenerFacturasCanceladas(LocalDate desde, LocalDate hasta) {
        return intendenciaRestClient
                .get()
                .uri(uri -> uri.path("/api/v1/utec/facturas/canceladas")
                        .queryParam("fecha_desde", desde.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .queryParam("fecha_hasta", hasta.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<
                        IntendenciaApiResponse<GeoPagosFacturaCanceladaDto>>() {
                });
    }
}
