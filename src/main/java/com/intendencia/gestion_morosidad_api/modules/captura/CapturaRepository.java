package com.intendencia.gestion_morosidad_api.modules.captura;

import static com.intendencia.gestion_morosidad_api.modules.captura.CapturaDtos.*;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CapturaRepository {
    private final JdbcTemplate jdbc;
    public Long crear(String nombre, LocalDateTime fecha, String usuario) {
        return jdbc.queryForObject("INSERT INTO capturas_cartera(nombre,fecha,usuario) VALUES (?,?,?) RETURNING id", Long.class, nombre, fecha, usuario);
    }
    public void guardarCuenta(Long id, EstadoCuenta c) {
        jdbc.update("""
            INSERT INTO captura_cuentas(captura_id,cm,origen,numero_padron,tipo_padron,nombre,documento,importe,estado,segmento,fecha_sincronizacion,fecha_ultimo_cobro,importe_ultimo_cobro)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,c.cm(),c.origen().name(),c.numeroPadron(),c.tipoPadron(),c.nombre(),c.documento(),c.importe(),c.estado(),c.segmento(),c.fechaSincronizacion(),c.fechaUltimoCobro(),c.importeUltimoCobro());
    }
    public List<Resumen> listar() {
        return jdbc.query("""
            SELECT c.id,c.nombre,c.fecha,c.usuario,count(d.cm) AS cantidad
            FROM capturas_cartera c JOIN captura_cuentas d ON d.captura_id=c.id
            GROUP BY c.id ORDER BY c.fecha DESC,c.id DESC
            """, (r,n) -> new Resumen(r.getLong("id"),r.getString("nombre"),r.getObject("fecha",LocalDateTime.class),r.getString("usuario"),r.getInt("cantidad")));
    }
    public Optional<Detalle> obtener(Long id) {
        var cabecera = jdbc.query("SELECT * FROM capturas_cartera WHERE id=?", (r,n) -> new Detalle(r.getLong("id"),r.getString("nombre"),r.getObject("fecha",LocalDateTime.class),r.getString("usuario"),List.of()),id);
        if (cabecera.isEmpty()) return Optional.empty();
        var filas = jdbc.query("SELECT * FROM captura_cuentas WHERE captura_id=? ORDER BY origen,cm",(r,n) -> new EstadoCuenta(
            r.getString("cm"),OrigenDatos.valueOf(r.getString("origen")),r.getString("numero_padron"),r.getString("tipo_padron"),r.getString("nombre"),r.getString("documento"),r.getBigDecimal("importe"),r.getString("estado"),r.getString("segmento"),r.getObject("fecha_sincronizacion",LocalDateTime.class),r.getObject("fecha_ultimo_cobro",java.time.LocalDate.class),r.getBigDecimal("importe_ultimo_cobro")),id);
        var c = cabecera.getFirst();
        return Optional.of(new Detalle(c.id(),c.nombre(),c.fecha(),c.usuario(),filas));
    }
}
