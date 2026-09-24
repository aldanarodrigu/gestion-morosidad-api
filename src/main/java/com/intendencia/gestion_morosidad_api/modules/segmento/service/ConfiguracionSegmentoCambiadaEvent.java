package com.intendencia.gestion_morosidad_api.modules.segmento.service;

/**
 * Se publica cuando cambian los rangos de los segmentos, para que el módulo de deudas
 * recalcule el segmento de cada deuda (sin que este módulo dependa de Deuda).
 */
public record ConfiguracionSegmentoCambiadaEvent() {
}
