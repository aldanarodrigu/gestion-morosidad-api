package com.intendencia.gestion_morosidad_api.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj con la zona horaria de Uruguay. Inyectarlo en lugar de usar LocalDate.now()
 * permite fijar la fecha en los tests (días de atraso, segmentos de mora).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Montevideo"));
    }
}
