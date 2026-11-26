package com.intendencia.gestion_morosidad_api.integration.intendencia.common;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CancellationException;

/** Seguimiento por hilo: no mezcla tareas manuales con automáticas y se libera al terminar. */
public final class SeguimientoSincronizacion implements AutoCloseable {
    private static final ThreadLocal<SeguimientoSincronizacion> ACTUAL = new ThreadLocal<>();
    private final SeguimientoSincronizacion anterior;
    private final Consumer<ProgresoSincronizacion> receptor;
    private final BooleanSupplier cancelada;
    private ProgresoSincronizacion progreso = ProgresoSincronizacion.preparando();

    private SeguimientoSincronizacion(Consumer<ProgresoSincronizacion> receptor, BooleanSupplier cancelada) {
        this.cancelada = cancelada;
        this.anterior = ACTUAL.get();
        this.receptor = receptor;
        ACTUAL.set(this);
        try { receptor.accept(progreso); }
        catch (RuntimeException | Error e) {
            close();
            throw e;
        }
    }

    public static SeguimientoSincronizacion abrir(Consumer<ProgresoSincronizacion> receptor) {
        return abrir(receptor, () -> false);
    }

    public static SeguimientoSincronizacion abrir(Consumer<ProgresoSincronizacion> receptor, BooleanSupplier cancelada) {
        return new SeguimientoSincronizacion(receptor,cancelada);
    }

    public static void comprobarCancelacion() {
        var actual = ACTUAL.get();
        if (actual != null && actual.cancelada.getAsBoolean())
            throw new CancellationException("Sincronización cancelada por el usuario.");
    }

    public static void etapa(String etapa, long registros) {
        comprobarCancelacion();
        var actual = ACTUAL.get();
        if (actual == null) return;
        String detalle = switch (etapa) {
            case "DEUDAS" -> "Consultando deudas pendientes del período.";
            case "COBROS" -> "Consultando los últimos cobros.";
            case "CONTACTOS" -> "Consultando datos de contacto.";
            case "GUARDANDO" -> "Conciliando y guardando en la base. Los cambios se confirman al finalizar.";
            case "FINALIZADA" -> "Sincronización completada.";
            default -> "Preparando sincronización.";
        };
        actual.publicar(new ProgresoSincronizacion(etapa,0,null,registros,null,null,1,detalle));
    }

    public static void verificandoPadrones(int completados, int total) {
        comprobarCancelacion();
        var actual = ACTUAL.get();
        if (actual == null) return;
        var p = actual.progreso;
        actual.publicar(new ProgresoSincronizacion(p.etapa(), p.paginasDescargadas(), p.paginaActual(),
                p.registrosDescargados(), p.totalRegistros(), p.totalPaginas(), 1,
                "Verificando padrones ausentes de la descarga (" + completados + " de " + total + ")."));
    }

    public static void solicitando(int pagina) {
        comprobarCancelacion();
        var actual = ACTUAL.get();
        if (actual == null) return;
        var p = actual.progreso;
        actual.publicar(new ProgresoSincronizacion(p.etapa(),p.paginasDescargadas(),pagina,
                p.registrosDescargados(),p.totalRegistros(),p.totalPaginas(),1,"Consultando página " + pagina + "."));
    }

    public static void pagina(int pagina, long registros, Long total, Integer totalPaginas, boolean quedan) {
        comprobarCancelacion();
        var actual = ACTUAL.get();
        if (actual == null) return;
        actual.publicar(new ProgresoSincronizacion(actual.progreso.etapa(),pagina,pagina,registros,
                total,totalPaginas,1,quedan ? "Página " + pagina + " validada; descargando las siguientes."
                        : "Descarga de esta etapa completa y validada."));
    }

    public static void reintentando(int pagina, int intento) {
        comprobarCancelacion();
        var actual = ACTUAL.get();
        if (actual == null) return;
        var p = actual.progreso;
        actual.publicar(new ProgresoSincronizacion(p.etapa(),p.paginasDescargadas(),pagina,
                p.registrosDescargados(),p.totalRegistros(),p.totalPaginas(),intento,
                "Reintentando página " + pagina + " (intento " + intento + " de 3)."));
    }

    private void publicar(ProgresoSincronizacion nuevo) {
        progreso = nuevo;
        receptor.accept(nuevo);
    }

    @Override public void close() {
        if (anterior == null) ACTUAL.remove();
        else ACTUAL.set(anterior);
    }
}
