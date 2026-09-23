package com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
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
}
