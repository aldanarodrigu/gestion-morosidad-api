package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
@Service
@RequiredArgsConstructor
public class PeriodoSincronizacionService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public int anioMinimo() { return jdbc.queryForObject("SELECT anio_minimo FROM configuracion_sincronizacion WHERE id=1", Integer.class); }
    public void guardar(int anio) {
        if (anio < 1900 || anio > LocalDate.now(clock).getYear())
            throw new ReglaNegocioException("El año mínimo debe estar entre 1900 y el año actual.");
        jdbc.update("UPDATE configuracion_sincronizacion SET anio_minimo=? WHERE id=1",anio);
    }
}
