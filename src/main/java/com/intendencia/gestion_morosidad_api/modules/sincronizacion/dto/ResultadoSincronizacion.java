package com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resumen de una ejecución de la sincronización con GeoPagos.
 *
 * @param advertencias registros omitidos y su motivo (solo CM / número de padrón, sin datos personales)
 */
public record ResultadoSincronizacion(
        LocalDateTime inicio,
        LocalDateTime fin,
        int pendientesRecibidas,
        int cobrosRecibidos,
        int deudasCreadas,
        int deudasActualizadas,
        int deudasCanceladas,
        int registrosOmitidos,
        List<String> advertencias) {
}
