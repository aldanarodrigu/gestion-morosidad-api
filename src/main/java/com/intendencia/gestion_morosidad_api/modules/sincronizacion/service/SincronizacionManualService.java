package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.OperacionEnCursoException;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion;
import java.time.*;
import java.util.UUID;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.ProgresoSincronizacion;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.SeguimientoSincronizacion;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.slf4j.LoggerFactory;
@Service
@RequiredArgsConstructor
public class SincronizacionManualService {
    private final SincronizacionDeudasService sincronizacion;
    private final PeriodoSincronizacionService periodo;
    private final Clock clock;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Ejecucion ultima;
    private AtomicBoolean cancelacion;
    private boolean aceptaCancelacion;
    private boolean activa() { return ultima != null && (ultima.estado().equals("EN_CURSO") || ultima.estado().equals("CANCELANDO")); }
    public record Ejecucion(UUID id, String estado, OrigenDatos origen, Integer anioMinimo, LocalDateTime inicio,
            LocalDateTime fin, String mensaje, ResultadoSincronizacion resultado, ProgresoSincronizacion progreso) {}
    public synchronized void guardar(Integer anio) {
        if (activa()) throw new OperacionEnCursoException("Ya hay una sincronización en curso.");
        sincronizacion.guardarAnio(anio);
    }
    public synchronized Ejecucion iniciar(OrigenDatos origen) {
        if (activa()) throw new OperacionEnCursoException("Ya hay una sincronización manual en curso.");
        Integer anio = periodo.anioMinimo();
        var inicio = LocalDateTime.now(clock);
        var token = new AtomicBoolean(false);
        cancelacion = token; aceptaCancelacion = true;
        var ejecucion = new Ejecucion(UUID.randomUUID(), "EN_CURSO", origen, anio, inicio, null,
                "Preparando sincronización.", null, ProgresoSincronizacion.preparando());
        ultima = ejecucion;
        executor.submit(() -> ejecutar(ejecucion, token));
        return ejecucion;
    }

    private void ejecutar(Ejecucion ejecucion, AtomicBoolean token) {
        ResultadoSincronizacion resultado;
        try (var seguimiento = SeguimientoSincronizacion.abrir(p -> actualizarProgreso(token, p), token::get)) {
            SeguimientoSincronizacion.comprobarCancelacion();
            resultado = sincronizacion.sincronizar(ejecucion.origen(), ejecucion.anioMinimo());
        } catch (Exception error) {
            finalizar(token, null, error);
            return;
        }
        finalizar(token, resultado, null);
    }

    private synchronized void actualizarProgreso(AtomicBoolean token, ProgresoSincronizacion progreso) {
        // La solicitud de cancelación y el comienzo del guardado comparten el mismo bloqueo.
        if (token.get()) throw new CancellationException();
        if (progreso.etapa().equals("GUARDANDO")) aceptaCancelacion = false;
        var actual = ultima;
        ultima = new Ejecucion(actual.id(), "EN_CURSO", actual.origen(), actual.anioMinimo(),
                actual.inicio(), null, progreso.detalle(), null, progreso);
    }

    private synchronized void finalizar(AtomicBoolean token, ResultadoSincronizacion resultado, Exception error) {
        var actual = ultima;
        var fin = LocalDateTime.now(clock);
        aceptaCancelacion = false;
        if (token.get() || error instanceof CancellationException) {
            ultima = new Ejecucion(actual.id(), "CANCELADA", actual.origen(), actual.anioMinimo(), actual.inicio(),
                    fin, "Sincronización cancelada. Se descartó la descarga y no se modificó la base.",
                    null, actual.progreso());
        } else if (error != null) {
            LoggerFactory.getLogger(getClass()).error("Sincronización manual {} falló", actual.origen(), error);
            String mensaje = error instanceof IntegracionExternaException || error instanceof OperacionEnCursoException
                    ? error.getMessage() : "No se pudo completar la sincronización. Revisá el registro del backend.";
            ultima = new Ejecucion(actual.id(), "ERROR", actual.origen(), actual.anioMinimo(),
                    actual.inicio(), fin, mensaje, null, actual.progreso());
        } else {
            ultima = new Ejecucion(actual.id(), "COMPLETADA", actual.origen(), actual.anioMinimo(), actual.inicio(),
                    fin, "Sincronización completada.", resultado, actual.progreso());
        }
    }

    public synchronized Ejecucion cancelar(UUID id) {
        if (ultima == null || !ultima.id().equals(id))
            throw new OperacionEnCursoException("La ejecución cambió. Revisá el estado actualizado antes de cancelar.");
        if (!activa() || ultima.estado().equals("CANCELANDO")) return ultima;
        if (!aceptaCancelacion) throw new ReglaNegocioException("El guardado ya comenzó; esperá a que termine para evitar cambios parciales.");
        cancelacion.set(true);
        var actual = ultima;
        ultima = new Ejecucion(actual.id(), "CANCELANDO", actual.origen(), actual.anioMinimo(), actual.inicio(), null,
                "Cancelación solicitada. Esperando que finalice la petición actual; no se guardarán los datos descargados.",
                null, actual.progreso());
        return ultima;
    }

    public Ejecucion estado() { return ultima; }
    @PreDestroy void cerrar() { executor.shutdown(); }
}
