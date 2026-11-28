package com.intendencia.gestion_morosidad_api.modules.cancelacion;

import com.intendencia.gestion_morosidad_api.shared.busqueda.TextoBusqueda;
import com.intendencia.gestion_morosidad_api.shared.dto.PaginaResponse;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CancelacionRepository {
    private static final String ORDEN = " ORDER BY fecha_cobro DESC, id DESC";

    private record Consulta(String condicion, MapSqlParameterSource parametros) {}
    private record ClaveCobro(OrigenDatos origen, String cm, String numeroCobro) {}

    private static final Comparator<String> TEXTO_OPCIONAL = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<Cancelacion> ORDEN_REPRESENTANTE =
            Comparator.comparing(Cancelacion::fechaCobro, Comparator.reverseOrder())
                    .thenComparing(c -> c.documento() == null || c.persona() == null)
                    .thenComparing(Cancelacion::documento, TEXTO_OPCIONAL)
                    .thenComparing(Cancelacion::persona, TEXTO_OPCIONAL);

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate consultas;

    public void guardar(List<Cancelacion> cancelaciones) {
        if (cancelaciones.isEmpty()) return;

        // La fuente puede repetir el mismo cobro y CM por cada persona asociada.
        // PostgreSQL admite una sola actualización de cada clave dentro del INSERT del lote.
        var porCobro = new LinkedHashMap<ClaveCobro, Cancelacion>();
        for (var cancelacion : cancelaciones) {
            var clave = new ClaveCobro(cancelacion.origen(), cancelacion.cm(), cancelacion.numeroCobro());
            porCobro.merge(clave, cancelacion, CancelacionRepository::consolidar);
        }
        jdbc.batchUpdate("""
                INSERT INTO cancelaciones (
                    origen, cm, numero_cobro, numero_padron, persona, documento,
                    fecha_cobro, importe_total, motivo, detectada_en
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (origen, cm, numero_cobro) DO UPDATE SET
                    numero_padron = EXCLUDED.numero_padron, persona = EXCLUDED.persona,
                    documento = EXCLUDED.documento, fecha_cobro = EXCLUDED.fecha_cobro,
                    importe_total = coalesce(EXCLUDED.importe_total, cancelaciones.importe_total),
                    motivo = coalesce(EXCLUDED.motivo, cancelaciones.motivo)
                """, porCobro.values(), 500, (ps, c) -> {
            ps.setString(1, c.origen().name());
            ps.setString(2, c.cm());
            ps.setString(3, c.numeroCobro());
            ps.setString(4, c.numeroPadron());
            ps.setString(5, c.persona());
            ps.setString(6, c.documento());
            ps.setObject(7, c.fechaCobro());
            ps.setBigDecimal(8, c.importeTotal());
            ps.setString(9, motivoInformado(c.motivo()));
            ps.setObject(10, c.detectadaEn());
        });
    }

    private static Cancelacion consolidar(Cancelacion primera, Cancelacion segunda) {
        boolean primeraRepresenta = ORDEN_REPRESENTANTE.compare(primera, segunda) <= 0;
        Cancelacion principal = primeraRepresenta ? primera : segunda;
        Cancelacion otra = primeraRepresenta ? segunda : primera;
        if (!principal.fechaCobro().equals(otra.fechaCobro())) return principal;

        BigDecimal importe = principal.importeTotal() == null ? otra.importeTotal() : principal.importeTotal();
        String motivo = motivoInformado(principal.motivo());
        if (motivo == null) motivo = motivoInformado(otra.motivo());
        return new Cancelacion(principal.id(), principal.origen(), principal.cm(), principal.numeroCobro(),
                principal.numeroPadron(), principal.persona(), principal.documento(), principal.fechaCobro(),
                importe, motivo, principal.detectadaEn());
    }

    private static String motivoInformado(String motivo) {
        return motivo == null || motivo.isBlank() ? null : motivo;
    }

    public List<Cancelacion> ultimosPorCm(OrigenDatos origen, Set<String> cms) {
        if (cms.isEmpty()) return List.of();
        return consultas.query("""
                SELECT DISTINCT ON (cm) *
                FROM cancelaciones
                WHERE origen = :origen AND cm = ANY(:cms)
                ORDER BY cm, fecha_cobro DESC, id DESC
                """, new MapSqlParameterSource("origen", origen.name())
                .addValue("cms", new SqlArrayValue("text", cms.toArray())), this::mapear);
    }

    public PaginaResponse<Cancelacion> listar(FiltroCancelaciones filtro, Pageable pagina) {
        var consulta = filtrar(filtro);
        var parametros = consulta.parametros();
        long total = consultas.queryForObject("SELECT count(*)" + consulta.condicion(), parametros, Long.class);
        parametros.addValue("limite", pagina.getPageSize()).addValue("offset", pagina.getOffset());
        var filas = consultas.query("SELECT *" + consulta.condicion() + ORDEN + " LIMIT :limite OFFSET :offset",
                parametros, this::mapear);
        return PaginaResponse.de(new PageImpl<>(filas, pagina, total));
    }

    public Stream<Cancelacion> recorrer(FiltroCancelaciones filtro) {
        var consulta = filtrar(filtro);
        return consultas.queryForStream("SELECT *" + consulta.condicion() + ORDEN,
                consulta.parametros(), this::mapear);
    }

    private Consulta filtrar(FiltroCancelaciones filtro) {
        filtro.validarFechas();
        var sql = new StringBuilder(" FROM cancelaciones WHERE TRUE");
        var parametros = new MapSqlParameterSource();
        if (filtro.origen() != null) {
            sql.append(" AND origen = :origen");
            parametros.addValue("origen", filtro.origen().name());
        }
        if (filtro.desde() != null) {
            sql.append(" AND fecha_cobro >= :desde");
            parametros.addValue("desde", filtro.desde());
        }
        if (filtro.hasta() != null) {
            sql.append(" AND fecha_cobro <= :hasta");
            parametros.addValue("hasta", filtro.hasta());
        }
        if (filtro.motivo() != null && !filtro.motivo().isBlank()) {
            sql.append(" AND upper(btrim(motivo)) = upper(:motivo)");
            parametros.addValue("motivo", filtro.motivo().strip());
        }
        if (filtro.buscar() != null && !filtro.buscar().isBlank()) {
            sql.append("""
                     AND (cm = :texto OR numero_padron = :texto OR documento = :texto
                          OR numero_cobro = :texto
                          OR strpos(translate(lower(persona), :conTilde, :sinTilde), :textoNormalizado) > 0)
                    """);
            parametros.addValue("texto", filtro.buscar().strip());
            parametros.addValue("textoNormalizado", TextoBusqueda.normalizar(filtro.buscar()));
            parametros.addValue("conTilde", TextoBusqueda.CON_TILDE);
            parametros.addValue("sinTilde", TextoBusqueda.SIN_TILDE);
        }
        return new Consulta(sql.toString(), parametros);
    }

    public List<String> listarMotivos() {
        return jdbc.queryForList("""
                SELECT DISTINCT upper(btrim(motivo))
                FROM cancelaciones
                WHERE btrim(motivo) <> ''
                ORDER BY 1
                """, String.class);
    }

    public Optional<Cancelacion> buscar(long id) {
        return jdbc.query("SELECT * FROM cancelaciones WHERE id = ?", this::mapear, id).stream().findFirst();
    }

    private Cancelacion mapear(ResultSet rs, int fila) throws SQLException {
        return new Cancelacion(rs.getLong("id"), OrigenDatos.valueOf(rs.getString("origen")),
                rs.getString("cm"), rs.getString("numero_cobro"), rs.getString("numero_padron"),
                rs.getString("persona"), rs.getString("documento"), rs.getObject("fecha_cobro", LocalDate.class),
                rs.getObject("importe_total", BigDecimal.class), rs.getString("motivo"),
                rs.getObject("detectada_en", LocalDateTime.class));
    }
}
