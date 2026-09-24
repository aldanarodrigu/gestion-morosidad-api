package com.intendencia.gestion_morosidad_api.modules.deuda.entity;

/**
 * Estado de seguimiento de la deuda dentro del sistema.
 * Las reglas definitivas de transición están pendientes de confirmar con la Intendencia (CU 2.17).
 */
public enum EstadoDeuda {
    PENDIENTE,
    EN_GESTION,
    EN_CONVENIO,
    CANCELADA
}
