package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaCanceladaDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import com.intendencia.gestion_morosidad_api.shared.exception.OperacionEnCursoException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sincroniza las deudas con GeoPagos (CU 2.16 / 2.17):
 * /facturas/pendientes alimenta monto, fechas, tributos, estado y segmento;
 * /facturas/canceladas aporta el último cobro de cada padrón.
 * Las consultas a la API se hacen fuera de la transacción para no bloquear la base mientras se espera la respuesta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SincronizacionDeudasService {

    private final GeoPagosClient geoPagosClient;
    private final SincronizacionDeudasProcessor processor;
    private final DeudaRepository deudaRepository;
    private final Clock clock;

    @Value("${geopagos.username:}")
    private String usuarioApi;

    /** Días hacia atrás para consultar cobros en la primera sincronización. */
    @Value("${sincronizacion.dias-cobros-iniciales:90}")
    private int diasCobrosIniciales;

    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    public ResultadoSincronizacion sincronizar() {
        if (usuarioApi == null || usuarioApi.isBlank()) {
            throw new IntegracionExternaException(
                    "Faltan las credenciales de la API GeoPagos (UTEC_API_USERNAME / UTEC_API_PASSWORD)");
        }
        if (!enCurso.compareAndSet(false, true)) {
            throw new OperacionEnCursoException("Ya hay una sincronización en curso");
        }
        try {
            LocalDateTime inicio = LocalDateTime.now(clock);
            LocalDate hoy = inicio.toLocalDate();

            List<GeoPagosFacturaPendienteDto> pendientes =
                    consultar("facturas pendientes", geoPagosClient::obtenerFacturasPendientes);

            // Cobros desde la sincronización anterior (con un día de margen); fecha_hasta no se incluye
            LocalDate desde = deudaRepository.ultimaSincronizacion()
                    .map(fecha -> fecha.toLocalDate().minusDays(1))
                    .orElse(hoy.minusDays(diasCobrosIniciales));
            List<GeoPagosFacturaCanceladaDto> canceladas = consultar("facturas canceladas",
                    () -> geoPagosClient.obtenerFacturasCanceladas(desde, hoy.plusDays(1)));

            return processor.procesar(pendientes, canceladas, hoy, inicio);
        } finally {
            enCurso.set(false);
        }
    }

    private <T> List<T> consultar(String nombre, Supplier<IntendenciaApiResponse<T>> llamada) {
        try {
            IntendenciaApiResponse<T> respuesta = llamada.get();
            if (respuesta == null || respuesta.getData() == null) {
                throw new IntegracionExternaException("GeoPagos devolvió una respuesta vacía al consultar " + nombre);
            }
            return respuesta.getData();
        } catch (RestClientResponseException ex) {
            // No se propaga el cuerpo de la respuesta al cliente: solo el código HTTP
            log.error("GeoPagos respondió HTTP {} al consultar {}", ex.getStatusCode().value(), nombre);
            throw new IntegracionExternaException(
                    "GeoPagos respondió HTTP " + ex.getStatusCode().value() + " al consultar " + nombre, ex);
        } catch (RestClientException ex) {
            log.error("No se pudo conectar con GeoPagos al consultar {}", nombre, ex);
            throw new IntegracionExternaException("No se pudo conectar con GeoPagos al consultar " + nombre, ex);
        }
    }
}
