package com.intendencia.gestion_morosidad_api.modules.deuda.service;
import com.intendencia.gestion_morosidad_api.modules.deuda.dto.HistorialPadronResponse;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

@Service
@RequiredArgsConstructor
public class HistorialPadronService {
    private final DeudaRepository deudas;
    private final RestClient intendenciaRestClient;
    public HistorialPadronResponse consultar(Long deudaId) {
        var deuda = deudas.findById(deudaId).orElseThrow(() -> new RecursoNoEncontradoException("No existe la deuda con id " + deudaId));
        var padron = deuda.getPadron();
        String ruta = padron.getOrigen() == OrigenDatos.SUCIVE
            ? "/api/v1/utec/sucive/contribuyentes/historial" : "/api/v1/utec/contribuyentes/historial";
        try {
            var respuesta = intendenciaRestClient.get().uri(uri -> uri.path(ruta).queryParam("cm", padron.getCm()).build())
                .retrieve().body(HistorialPadronResponse.class);
            if (respuesta == null || respuesta.data() == null || respuesta.meta() == null
                || respuesta.meta().total() == null || respuesta.meta().total() != respuesta.data().size()
                || respuesta.data().stream().anyMatch(r -> r == null || r.cmPadron() == null))
                throw new IntegracionExternaException("La API externa devolvió un historial de contribuyentes incompleto.");
            return respuesta;
        } catch (RestClientResponseException ex) {
            throw new IntegracionExternaException("La API externa respondió HTTP " + ex.getStatusCode().value() + " al consultar el historial del padrón.", ex);
        } catch (RestClientException ex) {
            throw new IntegracionExternaException("No se pudo consultar el historial del padrón en la API externa.", ex);
        }
    }
}
