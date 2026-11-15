package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.OperacionEnCursoException;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion;
import java.time.*;
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
    public record Ejecucion(String estado, OrigenDatos origen, int anioMinimo, LocalDateTime inicio,
        LocalDateTime fin, String mensaje, ResultadoSincronizacion resultado, ProgresoSincronizacion progreso) {
        public Ejecucion(String estado, OrigenDatos origen, int anioMinimo, LocalDateTime inicio,
                LocalDateTime fin, String mensaje, ResultadoSincronizacion resultado) {
            this(estado,origen,anioMinimo,inicio,fin,mensaje,resultado,null);
        }
    }
    public synchronized void guardar(int anio) {
        if (activa()) throw new OperacionEnCursoException("Ya hay una sincronización en curso.");
        sincronizacion.guardarAnio(anio);
    }
    public synchronized Ejecucion iniciar(OrigenDatos origen) {
        if (activa()) throw new OperacionEnCursoException("Ya hay una sincronización manual en curso.");
        int anio = periodo.anioMinimo();
        var inicio = LocalDateTime.now(clock);
        var token = new AtomicBoolean(false);
        cancelacion = token; aceptaCancelacion = true;
        ultima = new Ejecucion("EN_CURSO",origen,anio,inicio,null,"Preparando sincronización.",null,ProgresoSincronizacion.preparando());
        executor.submit(() -> {
            try (var seguimiento = SeguimientoSincronizacion.abrir(p -> actualizarProgreso(origen,anio,inicio,token,p), token::get)) {
                SeguimientoSincronizacion.comprobarCancelacion();
                var resultado = sincronizacion.sincronizar(origen,anio);
                SeguimientoSincronizacion.comprobarCancelacion();
                ultima = new Ejecucion("COMPLETADA",origen,anio,inicio,LocalDateTime.now(clock),"Sincronización completada.",resultado,ultima.progreso());
            } catch (CancellationException e) {
                ultima = new Ejecucion("CANCELADA",origen,anio,inicio,LocalDateTime.now(clock),
                    "Sincronización cancelada. Se descartó la descarga y no se modificó la base.",null,ultima.progreso());
            } catch (Exception e) {
                if (token.get()) {
                    ultima = new Ejecucion("CANCELADA",origen,anio,inicio,LocalDateTime.now(clock),
                        "Sincronización cancelada. Se descartó la descarga y no se modificó la base.",null,ultima.progreso());
                    return;
                }
                LoggerFactory.getLogger(getClass()).error("Sincronización manual {} falló",origen,e);
                String mensaje = e instanceof IntegracionExternaException || e instanceof OperacionEnCursoException
                    ? e.getMessage() : "No se pudo completar la sincronización. Revisá el registro del backend.";
                ultima = new Ejecucion("ERROR",origen,anio,inicio,LocalDateTime.now(clock),mensaje,null,ultima.progreso());
            }
        });
        return ultima;
    }
    private synchronized void actualizarProgreso(OrigenDatos origen, int anio, LocalDateTime inicio,
            AtomicBoolean token, ProgresoSincronizacion progreso) {
        // La solicitud de cancelación y el comienzo del guardado comparten el mismo bloqueo.
        if (token.get()) throw new CancellationException();
        if (progreso.etapa().equals("GUARDANDO")) aceptaCancelacion = false;
        ultima = new Ejecucion("EN_CURSO",origen,anio,inicio,null,progreso.detalle(),null,progreso);
    }

    public synchronized Ejecucion cancelar() {
        if (!activa()) return ultima;
        if (!aceptaCancelacion) throw new ReglaNegocioException("El guardado ya comenzó; esperá a que termine para evitar cambios parciales.");
        cancelacion.set(true);
        var e = ultima;
        ultima = new Ejecucion("CANCELANDO",e.origen(),e.anioMinimo(),e.inicio(),null,
            "Cancelación solicitada. Esperando que finalice la petición actual; no se guardarán los datos descargados.",null,e.progreso());
        return ultima;
    }

    public Ejecucion estado() { return ultima; }
    @PreDestroy void cerrar() { executor.shutdown(); }
}
