package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Sincronización automática. Deshabilitada por defecto: se activa con
 * SINCRONIZACION_AUTOMATICA=true (y opcionalmente SINCRONIZACION_CRON; por defecto todos los días a las 3:00).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sincronizacion.automatica.habilitada", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SincronizacionProgramada {

    private final SincronizacionDeudasService sincronizacionDeudasService;

    @Scheduled(cron = "${sincronizacion.automatica.cron:0 0 3 * * *}", zone = "America/Montevideo")
    public void ejecutar() {
        try {
            sincronizacionDeudasService.sincronizar();
        } catch (RuntimeException ex) {
            // Se registra y se reintenta en la próxima ejecución programada
            log.error("Falló la sincronización automática con GeoPagos: {}", ex.getMessage());
        }
    }
}
