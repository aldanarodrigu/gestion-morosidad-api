package com.intendencia.gestion_morosidad_api.modules.deuda.service;

import com.intendencia.gestion_morosidad_api.modules.deuda.dto.FacturasPadronResponse;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

@Service
@RequiredArgsConstructor
public class FacturasPadronService {
    private final DeudaRepository deudas;
    private final RestClient intendenciaRestClient;

    public FacturasPadronResponse consultar(Long deudaId, String estado, Boolean vencida, boolean incluirConvenios) {
        if (estado != null && !estado.isBlank() && !estado.matches("(?i)PENDIENTE|CANCELADA|[A-Z0-9]"))
            throw new ReglaNegocioException("Estado de factura inválido.");
        var deuda = deudas.findById(deudaId).orElseThrow(() -> new RecursoNoEncontradoException("No existe la deuda con id " + deudaId));
        var padron = deuda.getPadron();
        String ruta = padron.getOrigen() == OrigenDatos.SUCIVE ? "/api/v1/utec/sucive/facturas" : "/api/v1/utec/facturas";
        try {
            var respuesta = intendenciaRestClient.get().uri(uri -> {
                uri.path(ruta).queryParam("cm", padron.getCm()).queryParam("incluir_convenios", incluirConvenios ? 1 : 0);
                if (estado != null && !estado.isBlank()) uri.queryParam("estado", estado);
                if (vencida != null) uri.queryParam("vencida", vencida ? 1 : 0);
                return uri.build();
            }).retrieve().body(FacturasPadronResponse.class);
            if (respuesta == null || respuesta.data() == null || respuesta.meta() == null
                    || respuesta.meta().total() == null || respuesta.meta().total() != respuesta.data().size())
                throw new IntegracionExternaException("La API externa devolvió una respuesta de facturas incompleta.");
            return respuesta;
        } catch (RestClientResponseException ex) {
            throw new IntegracionExternaException("La API externa respondió HTTP " + ex.getStatusCode().value() + " al consultar las facturas del padrón.", ex);
        } catch (RestClientException ex) {
            throw new IntegracionExternaException("No se pudieron consultar las facturas del padrón. La API externa no respondió correctamente.", ex);
        }
    }
}
