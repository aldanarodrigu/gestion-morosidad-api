package com.intendencia.gestion_morosidad_api.integration.intendencia.geopat.client;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaPaginador;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.*;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopat.dto.GeoPatContribuyenteDto;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/** Rutas vigentes de SUCIVE, con la misma URL base y Basic Auth de GeoPagos. */
@Component
@RequiredArgsConstructor
public class GeoPatClient implements IntendenciaClient {
    private final IntendenciaPaginador paginador;

    @Override public IntendenciaApiResponse<GeoPagosFacturaPendienteDto> obtenerFacturasPendientes(LocalDate desde, LocalDate hasta) {
        return paginador.obtenerSinTotal("/api/v1/utec/sucive/facturas/pendientes", desde == null ? Map.of("fecha_corte", hasta.toString()) : Map.of("fecha_inicio", desde.toString(), "fecha_corte", hasta.toString()),
                new ParameterizedTypeReference<>() {}, GeoPagosFacturaPendienteDto::cm);
    }

    @Override
    public IntendenciaApiResponse<GeoPagosFacturaPendienteDto> obtenerFacturasPendientes() {
        return paginador.obtenerTodo("/api/v1/utec/sucive/facturas/pendientes", Map.of(),
                new ParameterizedTypeReference<>() {}, GeoPagosFacturaPendienteDto::cm);
    }

    @Override
    public IntendenciaApiResponse<GeoPagosFacturaCanceladaDto> obtenerFacturasCanceladas(LocalDate desde, LocalDate hasta) {
        return paginador.obtenerTodo("/api/v1/utec/sucive/facturas/canceladas",
                Map.of("fecha_desde", desde.toString(), "fecha_hasta", hasta.toString()),
                new ParameterizedTypeReference<>() {}, null);
    }

    @Override
    public IntendenciaApiResponse<GeoPagosContribuyenteDto> obtenerContribuyentes() {
        IntendenciaApiResponse<GeoPatContribuyenteDto> respuesta = paginador.obtenerTodo(
                "/api/v1/utec/sucive/contribuyentes", Map.of(), new ParameterizedTypeReference<>() {}, GeoPatContribuyenteDto::cm);
        return new IntendenciaApiResponse<>(respuesta.getData().stream().map(d ->
                new GeoPagosContribuyenteDto(d.cm(), d.numeroPadron(), d.tipoPadron(), d.localidad(),
                        d.block(), d.unidad(), d.nombrePersonas(), d.telefonoPersonas(), d.emailPersonas(),
                        null, null, null, null, d.matricula())).toList(), respuesta.getMeta());
    }
}
