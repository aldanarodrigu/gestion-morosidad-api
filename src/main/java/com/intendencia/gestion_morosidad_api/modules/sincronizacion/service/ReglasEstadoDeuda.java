package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;

/**
 * Cambios automáticos de estado durante la sincronización (CU 2.17).
 * Reglas provisorias: las definitivas están pendientes de confirmar con la Intendencia.
 */
public final class ReglasEstadoDeuda {

    private ReglasEstadoDeuda() {
    }

    /**
     * Estado de una deuda que sigue apareciendo en /facturas/pendientes.
     * <ul>
     *   <li>Con convenio activo → EN_CONVENIO.</li>
     *   <li>Estaba CANCELADA o EN_CONVENIO y ya no → vuelve a PENDIENTE (deuda nueva o convenio caído).</li>
     *   <li>PENDIENTE o EN_GESTION → se mantiene (EN_GESTION lo pone un usuario y la sincronización no lo pisa).</li>
     * </ul>
     */
    public static EstadoDeuda enPendientes(EstadoDeuda actual, boolean tieneConvenio) {
        if (tieneConvenio) {
            return EstadoDeuda.EN_CONVENIO;
        }
        if (actual == null || actual == EstadoDeuda.CANCELADA || actual == EstadoDeuda.EN_CONVENIO) {
            return EstadoDeuda.PENDIENTE;
        }
        return actual;
    }
}
