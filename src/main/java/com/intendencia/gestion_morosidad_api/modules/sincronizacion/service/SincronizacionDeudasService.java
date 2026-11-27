package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaCanceladaDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import com.intendencia.gestion_morosidad_api.shared.exception.OperacionEnCursoException;
import java.time.Clock;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.SeguimientoSincronizacion;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopat.client.GeoPatClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaClient;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sincroniza las deudas con GeoPagos o GeoPat, con conciliación y fecha anterior por origen:
 * /facturas/pendientes alimenta monto, fechas, tributos, estado y segmento;
 * /facturas/canceladas aporta el último cobro de cada padrón.
 * Las consultas a la API se hacen fuera de la transacción para no bloquear la base mientras se espera la respuesta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SincronizacionDeudasService {

    private final GeoPagosClient geoPagosClient;
    private final GeoPatClient geoPatClient;
    private final SincronizacionDeudasProcessor processor;
    private final DeudaRepository deudaRepository;
    private final Clock clock;
    private final PeriodoSincronizacionService periodo;

    @Value("${geopagos.username:}")
    private String usuarioApi;

    /** Días hacia atrás para consultar cobros en la primera sincronización. */
    @Value("${sincronizacion.dias-cobros-iniciales:90}")
    private int diasCobrosIniciales;

    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    public ResultadoSincronizacion sincronizar() {
        return sincronizar(OrigenDatos.INTENDENCIA);
    }

    public synchronized void guardarAnio(Integer anio) {
        if (enCurso.get()) throw new OperacionEnCursoException("No se puede cambiar el período durante una sincronización.");
        periodo.guardar(anio);
    }

    public ResultadoSincronizacion sincronizar(OrigenDatos origen) {
        return sincronizar(origen, periodo.anioMinimo());
    }

    public ResultadoSincronizacion sincronizar(OrigenDatos origen, Integer anioMinimo) {
        Objects.requireNonNull(origen);
        IntendenciaClient client = origen == OrigenDatos.SUCIVE ? geoPatClient : geoPagosClient;
        if (usuarioApi == null || usuarioApi.isBlank()) {
            throw new IntegracionExternaException(
                    "Faltan las credenciales de la API de Intendencia (UTEC_API_USERNAME / UTEC_API_PASSWORD)");
        }
        if (!enCurso.compareAndSet(false, true)) {
            throw new OperacionEnCursoException("Ya hay una sincronización en curso");
        }
        try {
            LocalDateTime inicio = LocalDateTime.now(clock);
            LocalDate hoy = inicio.toLocalDate();

            LocalDate inicioPeriodo = anioMinimo == null ? null : LocalDate.of(anioMinimo, 1, 1);
            SeguimientoSincronizacion.etapa("DEUDAS",0);
            List<GeoPagosFacturaPendienteDto> pendientes =
                    consultar(origen, "facturas pendientes", () -> client.obtenerFacturasPendientes(inicioPeriodo, hoy));
            verificarPadronesAusentes(client, origen, inicioPeriodo, hoy, pendientes);

            // Cobros desde la sincronización anterior (con un día de margen); fecha_hasta no se incluye
            LocalDate desde = deudaRepository.ultimaSincronizacionPorOrigen(origen)
                    .map(fecha -> fecha.toLocalDate().minusDays(1))
                    .orElse(hoy.minusDays(diasCobrosIniciales));
            SeguimientoSincronizacion.etapa("COBROS",0);
            List<GeoPagosFacturaCanceladaDto> canceladas = consultar(origen, "facturas canceladas",
                    () -> client.obtenerFacturasCanceladas(desde, hoy.plusDays(1)));

            // Contactos de Personas y del último GeoPago. Opcional: si falla se guardan las deudas
            // igual y los contactos se actualizan en la próxima sincronización (null = no disponible).
            List<GeoPagosContribuyenteDto> contribuyentes;
            SeguimientoSincronizacion.etapa("CONTACTOS",0);
            try {
                contribuyentes = consultar(origen, "contribuyentes", client::obtenerContribuyentes);
            } catch (IntegracionExternaException ex) {
                contribuyentes = null;
            }

            SeguimientoSincronizacion.etapa("GUARDANDO",pendientes.size());
            var resultado = processor.procesar(pendientes, canceladas, contribuyentes, hoy, inicio, origen, anioMinimo);
            // El proxy del procesador ya confirmó la transacción antes de anunciar la finalización.
            SeguimientoSincronizacion.etapa("FINALIZADA",pendientes.size());
            return resultado;
        } finally {
            enCurso.set(false);
        }
    }

    private void verificarPadronesAusentes(IntendenciaClient client, OrigenDatos origen, LocalDate desde,
                                           LocalDate hasta, List<GeoPagosFacturaPendienteDto> pendientes) {
        var recibidos = pendientes.stream().filter(Objects::nonNull)
                .map(fila -> SincronizacionDeudasProcessor.texto(fila.cm()))
                .collect(Collectors.toSet());
        var ausentes = deudaRepository.listarCmConDeudasParaConciliar(origen, EstadoDeuda.CANCELADA, desde == null, desde)
                .stream().filter(cm -> !recibidos.contains(cm)).toList();
        if (ausentes.isEmpty()) return;
        if (pendientes.isEmpty())
            throw new IntegracionExternaException(origen.proveedor()
                    + " no devolvió deudas pendientes para el período con deudas registradas; no se aplicaron cambios.");

        for (int indice = 0; indice < ausentes.size(); indice++) {
            String cm = ausentes.get(indice);
            SeguimientoSincronizacion.verificandoPadrones(indice, ausentes.size());
            var confirmacion = consultar(origen, "facturas pendientes del CM " + cm,
                    () -> client.obtenerFacturasPendientesPorCm(cm, desde, hasta));
            if (confirmacion.isEmpty()) continue;
            var deuda = confirmacion.getFirst();
            if (confirmacion.size() != 1 || deuda == null || !cm.equals(SincronizacionDeudasProcessor.texto(deuda.cm()))
                    || deuda.importeDeuda() == null || deuda.importeDeuda().signum() > 0)
                throw new IntegracionExternaException(origen.proveedor() + " devolvió una descarga de pendientes "
                        + "inconsistente con la consulta del CM " + cm + ". No se aplicaron cambios.");
        }
    }

    private <T> List<T> consultar(OrigenDatos origen, String nombre, Supplier<IntendenciaApiResponse<T>> llamada) {
        try {
            IntendenciaApiResponse<T> respuesta = llamada.get();
            if (respuesta == null || respuesta.getData() == null) {
                throw new IntegracionExternaException(origen.proveedor() + " devolvió una respuesta vacía al consultar " + nombre);
            }
            return respuesta.getData();
        } catch (RestClientResponseException ex) {
            // No se propaga el cuerpo de la respuesta al cliente: solo el código HTTP
            log.error("{} respondió HTTP {} al consultar {}", origen.proveedor(), ex.getStatusCode().value(), nombre);
            throw new IntegracionExternaException(
                    origen.proveedor() + " respondió HTTP " + ex.getStatusCode().value() + " al consultar " + nombre, ex);
        } catch (RestClientException ex) {
            if (ex.contains(JacksonException.class) || ex.contains(HttpMessageConversionException.class)) {
                log.error("{} devolvió datos inválidos al consultar {}", origen.proveedor(), nombre, ex);
                throw new IntegracionExternaException(origen.proveedor()
                        + " devolvió datos inválidos al consultar " + nombre + ".", ex);
            }
            log.error("No se pudo conectar con {} al consultar {}", origen.proveedor(), nombre, ex);
            throw new IntegracionExternaException("No se pudo conectar con " + origen.proveedor() + " al consultar " + nombre, ex);
        }
    }
}
