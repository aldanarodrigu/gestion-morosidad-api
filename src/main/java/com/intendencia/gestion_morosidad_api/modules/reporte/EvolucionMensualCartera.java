package com.intendencia.gestion_morosidad_api.modules.reporte;

import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EvolucionMensualCartera(
        LocalDate mes,
        OrigenDatos origen,
        boolean enCurso,
        LocalDateTime corte,
        Integer anioMinimo,
        BigDecimal saldoPendiente,
        long cantidadPagos,
        BigDecimal importePagos
) {
    EvolucionMensualCartera conSaldoActual(BigDecimal saldo) {
        return new EvolucionMensualCartera(mes, origen, enCurso, corte, anioMinimo, saldo, cantidadPagos, importePagos);
    }

    String estadoCorte() {
        if (enCurso) return "En curso";
        return corte == null ? "Sin cierre" : "Cerrado";
    }
}
