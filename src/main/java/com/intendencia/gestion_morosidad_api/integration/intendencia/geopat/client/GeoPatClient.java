package com.intendencia.gestion_morosidad_api.integration.intendencia.geopat.client;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.*;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopat.dto.GeoPatContribuyenteDto;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Rutas del Anexo GeoPat, con la misma URL base y Basic Auth del manual principal. */
@Component
@RequiredArgsConstructor
public class GeoPatClient implements IntendenciaClient {
    private final RestClient intendenciaRestClient;

    @Override
    public IntendenciaApiResponse<GeoPagosFacturaPendienteDto> obtenerFacturasPendientes() {
        return intendenciaRestClient.get().uri("/api/v1/utec/geopat/facturas/pendientes")
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    @Override
    public IntendenciaApiResponse<GeoPagosFacturaCanceladaDto> obtenerFacturasCanceladas(LocalDate desde, LocalDate hasta) {
        return intendenciaRestClient.get().uri(uri -> uri.path("/api/v1/utec/geopat/facturas/canceladas")
                        .queryParam("fecha_desde", desde.toString()).queryParam("fecha_hasta", hasta.toString()).build())
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    @Override
    public IntendenciaApiResponse<GeoPagosContribuyenteDto> obtenerContribuyentes() {
        IntendenciaApiResponse<GeoPatContribuyenteDto> respuesta = intendenciaRestClient.get()
                .uri("/api/v1/utec/geopat/contribuyentes").retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (respuesta == null || respuesta.getData() == null) return null;
        return new IntendenciaApiResponse<>(respuesta.getData().stream().map(d ->
                new GeoPagosContribuyenteDto(d.cm(), d.numeroPadron(), d.tipoPadron(), d.localidad(),
                        d.block(), d.unidad(), d.nombrePersonas(), d.telefonoPersonas(), d.emailPersonas(),
                        null, null, null, null)).toList(), respuesta.getMeta());
    }
}
