package com.intendencia.gestion_morosidad_api.modules.cancelacion;

import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record Cancelacion(
        Long id, OrigenDatos origen, String cm, String numeroCobro, String numeroPadron,
        String persona, String documento, LocalDate fechaCobro, BigDecimal importeTotal,
        String motivo, LocalDateTime detectadaEn) {}
