package com.intendencia.gestion_morosidad_api.modules.reporte;

import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class EvolucionMensualRepository {

    private final JdbcClient jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void guardarCierre(LocalDate fechaConsulta, OrigenDatos origen, LocalDateTime corte, Integer anioMinimo) {
        var mes = fechaConsulta.withDayOfMonth(1);
        int escritos = jdbc.sql("""
                INSERT INTO cierre_mensual_cartera (mes, origen, corte, anio_minimo)
                VALUES (:mes, :origen, :corte, :anioMinimo)
                ON CONFLICT (mes, origen) DO UPDATE
                SET corte = EXCLUDED.corte, anio_minimo = EXCLUDED.anio_minimo
                WHERE cierre_mensual_cartera.corte <= EXCLUDED.corte
                """)
                .param("mes", mes).param("origen", origen.name()).param("corte", corte)
                .param("anioMinimo", anioMinimo, Types.INTEGER).update();
        if (escritos == 0) return;

        jdbc.sql("DELETE FROM cierre_mensual_cartera_saldos WHERE mes = :mes AND origen = :origen")
                .param("mes", mes).param("origen", origen.name()).update();
        jdbc.sql("""
                INSERT INTO cierre_mensual_cartera_saldos (mes, origen, tipo_padron, segmento_id, importe)
                SELECT :mes, p.origen, nullif(trim(p.tipo_padron), ''), d.segmento_mora_id, sum(d.importe)
                FROM deuda d JOIN padrones p ON p.id = d.padron_id
                WHERE p.origen = :origen AND
                """ + ReporteRepository.CARTERA_VIGENTE + """
                GROUP BY p.origen, nullif(trim(p.tipo_padron), ''), d.segmento_mora_id
                """)
                .param("mes", mes).param("origen", origen.name()).update();
    }

    List<EvolucionMensualCartera> consultar(OrigenDatos origen, String tipoPadron, Long segmento, LocalDate hoy,
                                            List<ReporteCartera.Grupo> gruposActuales) {
        var mesActual = YearMonth.from(hoy);
        var saldosActuales = new EnumMap<OrigenDatos, BigDecimal>(OrigenDatos.class);
        for (var grupo : gruposActuales) {
            saldosActuales.merge(OrigenDatos.valueOf(grupo.origen()), grupo.importe(), BigDecimal::add);
        }
        var filas = jdbc.sql("""
                WITH origenes(origen) AS (
                    SELECT origen FROM (VALUES ('INTENDENCIA'), ('SUCIVE')) AS disponibles(origen)
                    WHERE :origen IS NULL OR origen = :origen
                ),
                cierres AS (
                    SELECT c.mes, c.origen, c.corte, c.anio_minimo, coalesce(sum(s.importe), 0) AS saldo
                    FROM cierre_mensual_cartera c JOIN origenes o ON o.origen = c.origen
                    LEFT JOIN cierre_mensual_cartera_saldos s ON s.mes = c.mes AND s.origen = c.origen
                        AND (:tipo IS NULL OR s.tipo_padron = :tipo)
                        AND (:segmento IS NULL OR s.segmento_id = :segmento)
                    WHERE c.mes BETWEEN :primerMes AND :mesActual
                    GROUP BY c.mes, c.origen, c.corte, c.anio_minimo
                ),
                cobros AS (
                    SELECT DISTINCT ON (c.origen, c.numero_cobro)
                           c.origen, c.fecha_cobro,
                           first_value(nullif(trim(c.motivo), '')) OVER (
                               PARTITION BY c.origen, c.numero_cobro, c.fecha_cobro
                               ORDER BY (nullif(trim(c.motivo), '') IS NULL), c.detectada_en DESC, c.id DESC
                           ) AS motivo,
                           first_value(c.importe_total) OVER (
                               PARTITION BY c.origen, c.numero_cobro, c.fecha_cobro
                               ORDER BY (c.importe_total IS NULL), c.detectada_en DESC, c.id DESC
                           ) AS importe_total
                    FROM cancelaciones c JOIN origenes o ON o.origen = c.origen
                    ORDER BY c.origen, c.numero_cobro, c.detectada_en DESC, c.id DESC
                ),
                pagos AS (
                    SELECT date_trunc('month', fecha_cobro)::date AS mes, origen,
                           count(*) AS cantidad, sum(importe_total) AS importe
                    FROM cobros
                    WHERE fecha_cobro BETWEEN :primerMes AND :hoy AND upper(trim(motivo)) = 'PAGO'
                    GROUP BY 1, 2
                ),
                meses AS (
                    SELECT mes::date
                    FROM generate_series(
                        least(:mesActual, (SELECT min(mes) FROM cierres), (SELECT min(mes) FROM pagos))::timestamp,
                        :mesActual::timestamp,
                        interval '1 month'
                    ) AS serie(mes)
                )
                SELECT m.mes, o.origen, m.mes = :mesActual AS en_curso, c.corte, c.anio_minimo,
                       c.saldo, coalesce(p.cantidad, 0) AS cantidad_pagos, p.importe AS importe_pagos
                FROM meses m CROSS JOIN origenes o
                LEFT JOIN cierres c ON c.mes = m.mes AND c.origen = o.origen
                LEFT JOIN pagos p ON p.mes = m.mes AND p.origen = o.origen
                ORDER BY m.mes DESC, o.origen
                """)
                .param("origen", origen == null ? null : origen.name(), Types.VARCHAR)
                .param("tipo", tipoPadron == null || tipoPadron.isBlank() ? null : tipoPadron.trim(), Types.VARCHAR)
                .param("segmento", segmento, Types.BIGINT)
                .param("primerMes", mesActual.minusMonths(11).atDay(1))
                .param("mesActual", mesActual.atDay(1)).param("hoy", hoy)
                .query((rs, numero) -> new EvolucionMensualCartera(
                        rs.getObject("mes", LocalDate.class), OrigenDatos.valueOf(rs.getString("origen")),
                        rs.getBoolean("en_curso"), rs.getObject("corte", LocalDateTime.class),
                        rs.getObject("anio_minimo", Integer.class), rs.getBigDecimal("saldo"),
                        rs.getLong("cantidad_pagos"), rs.getBigDecimal("importe_pagos")))
                .list();
        return filas.stream().map(fila -> fila.enCurso()
                ? fila.conSaldoActual(saldosActuales.getOrDefault(fila.origen(), BigDecimal.ZERO))
                : fila).toList();
    }
}
