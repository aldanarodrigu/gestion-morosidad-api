package com.intendencia.gestion_morosidad_api.modules.reporte;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import static com.intendencia.gestion_morosidad_api.modules.reporte.ReporteCartera.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReporteRepository {
    private final JdbcTemplate jdbc;
    private static final String PERSONA = "CASE WHEN nullif(trim(c.documento),'') IS NOT NULL THEN 'DOC:' || trim(c.documento) ELSE 'ID:' || c.id::text END";
    private static final String TIPO = "nullif(trim(p.tipo_padron),'')";
    public ReporteCartera consultar(OrigenDatos origen, String tipoPadron, Long segmento, LocalDateTime generado) {
        String from = " FROM deuda d JOIN padrones p ON p.id=d.padron_id JOIN contribuyentes c ON c.id=p.contribuyente_id LEFT JOIN segmentos_mora s ON s.id=d.segmento_mora_id WHERE d.estado <> 'CANCELADA' AND d.importe > 0";
        var args = new ArrayList<Object>();
        if (origen != null) { from += " AND p.origen=?"; args.add(origen.name()); }
        if (tipoPadron != null && !tipoPadron.isBlank()) { from += " AND trim(p.tipo_padron)=?"; args.add(tipoPadron.trim()); }
        if (segmento != null) { from += " AND d.segmento_mora_id=?"; args.add(segmento); }
        Object[] parametros = args.toArray();
        var totales = jdbc.queryForObject("SELECT coalesce(sum(d.importe),0) AS importe,count(distinct " + PERSONA + ") AS personas,count(distinct p.id) AS padrones,count(d.id) AS deudas" + from,
            (r,n) -> new Totales(r.getBigDecimal("importe"),r.getLong("personas"),r.getLong("padrones"),r.getLong("deudas")),parametros);
        var segmentos = jdbc.query("SELECT s.id,s.nombre,s.orden,count(distinct p.id) AS cantidad,sum(d.importe) AS importe" + from + " GROUP BY s.id,s.nombre,s.orden ORDER BY s.orden NULLS LAST,s.id",
            (r,n) -> new Segmento(r.getObject("id",Long.class),r.getString("nombre"),r.getObject("orden",Integer.class),r.getLong("cantidad"),r.getBigDecimal("importe")),parametros);
        var tipos = jdbc.query("SELECT " + TIPO + " AS tipo,count(distinct p.id) AS cantidad,sum(d.importe) AS importe" + from + " GROUP BY " + TIPO + " ORDER BY importe DESC,tipo NULLS LAST",
            (r,n) -> new Tipo(r.getString("tipo"),r.getLong("cantidad"),r.getBigDecimal("importe")),parametros);
        var grupos = jdbc.query("SELECT p.origen," + TIPO + " AS tipo,count(distinct " + PERSONA + ") AS personas,count(distinct p.id) AS cantidad,sum(d.importe) AS importe" + from + " GROUP BY p.origen," + TIPO + " ORDER BY p.origen,tipo NULLS LAST",
            (r,n) -> new Grupo(r.getString("origen"),r.getString("tipo"),r.getLong("personas"),r.getLong("cantidad"),r.getBigDecimal("importe")),parametros);
        return new ReporteCartera(generado,totales,segmentos,tipos,grupos);
    }
}
