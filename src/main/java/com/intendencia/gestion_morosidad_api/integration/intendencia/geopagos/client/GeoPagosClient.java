package com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.*;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.*;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeoPagosClient implements IntendenciaClient {
    private final IntendenciaPaginador paginador;

    @Override public IntendenciaApiResponse<GeoPagosContribuyenteDto> obtenerContribuyentes() {
        return paginador.obtenerTodo("/api/v1/utec/contribuyentes-geopagos", Map.of(),
                new ParameterizedTypeReference<>() {}, GeoPagosContribuyenteDto::cm);
    }

    /** Foto completa de deuda vencida: todas las paginas, sin filtros por padron. */
    @Override public IntendenciaApiResponse<GeoPagosFacturaPendienteDto> obtenerFacturasPendientes() {
        return paginador.obtenerTodo("/api/v1/utec/facturas/pendientes", Map.of(),
                new ParameterizedTypeReference<>() {}, GeoPagosFacturaPendienteDto::cm);
    }

    /** Todos los cobros del intervalo [desde, hasta), incluyendo sus paginas. */
    @Override public IntendenciaApiResponse<GeoPagosFacturaCanceladaDto> obtenerFacturasCanceladas(LocalDate desde, LocalDate hasta) {
        return paginador.obtenerTodo("/api/v1/utec/facturas/canceladas",
                Map.of("fecha_desde", desde.toString(), "fecha_hasta", hasta.toString()),
                new ParameterizedTypeReference<>() {}, null);
    }
}
