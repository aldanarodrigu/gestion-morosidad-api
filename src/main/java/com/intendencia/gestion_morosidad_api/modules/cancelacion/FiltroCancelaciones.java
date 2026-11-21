package com.intendencia.gestion_morosidad_api.modules.cancelacion;

import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record FiltroCancelaciones(
        OrigenDatos origen,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        String buscar,
        String motivo) {

    public void validarFechas() {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaNegocioException("La fecha hasta debe ser igual o posterior a la fecha desde.");
        }
    }
}
