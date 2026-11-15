package com.intendencia.gestion_morosidad_api.modules.convenio;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

@Service
@RequiredArgsConstructor
public class ConvenioService {
    private final RestClient intendenciaRestClient;
    public IntendenciaApiResponse<ConvenioResponse> consultar(OrigenDatos origen, int pagina, int tamanio,
            Long cm, String documento, String nombre, LocalDate desde, LocalDate hasta) {
        if (pagina < 1 || pagina > 1000000 || tamanio < 1 || tamanio > 100 || (cm != null && cm < 1))
            throw new ReglaNegocioException("Paginación o CM inválidos.");
        if ((nombre != null && nombre.length() > 150) || (documento != null && documento.length() > 150))
            throw new ReglaNegocioException("Los filtros no pueden superar 150 caracteres.");
        if (desde != null && !desde.isBefore(hasta == null ? LocalDate.now() : hasta))
            throw new ReglaNegocioException("La fecha hasta debe ser posterior a la fecha desde.");
        String ruta = origen == OrigenDatos.SUCIVE ? "/api/v1/utec/sucive/convenios/pendientes" : "/api/v1/utec/convenios/pendientes";
        try {
            var respuesta = intendenciaRestClient.get().uri(uri -> {
                uri.path(ruta).queryParam("page", pagina).queryParam("per_page", tamanio);
                if (cm != null) uri.queryParam("cm", cm);
                if (documento != null && !documento.isBlank()) uri.queryParam("documento", documento.trim());
                if (nombre != null && !nombre.isBlank()) uri.queryParam("nombre", nombre.trim());
                if (desde != null) uri.queryParam("fecha_inicio", desde);
                if (hasta != null) uri.queryParam("fecha_corte", hasta);
                return uri.build();
            }).retrieve().body(new ParameterizedTypeReference<IntendenciaApiResponse<ConvenioResponse>>() {});
            if (respuesta == null || respuesta.getData() == null || respuesta.getMeta() == null)
                throw new IntegracionExternaException("Respuesta de convenios incompleta.");
            var m = respuesta.getMeta();
            if (m.getTotal() == null || m.getTotal() < 0 || m.getCurrentPage() == null || m.getCurrentPage() != pagina
                    || m.getPerPage() == null || m.getPerPage() != tamanio || m.getHasMore() == null
                    || respuesta.getData().size() > tamanio || respuesta.getData().stream().anyMatch(c -> c == null || c.CM() == null))
                throw new IntegracionExternaException("Metadatos o registros de convenios inválidos.");
            return respuesta;
        } catch (RestClientResponseException ex) {
            throw new IntegracionExternaException("La API externa respondió HTTP " + ex.getStatusCode().value() + " al consultar convenios.", ex);
        } catch (RestClientException ex) {
            throw new IntegracionExternaException("No se pudo consultar convenios en la API externa.", ex);
        }
    }
}
