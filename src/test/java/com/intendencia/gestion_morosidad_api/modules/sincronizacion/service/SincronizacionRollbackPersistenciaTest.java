package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaCanceladaDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Una sincronización que falla a mitad de camino no deja nada guardado: ni deudas nuevas, ni
 * importes o estados actualizados, ni cancelaciones, cobros, padrones, contribuyentes o contactos.
 *
 * <p>A diferencia de {@link SincronizacionCasosRealesPersistenciaTest}, esta clase <b>no</b> es
 * {@code @Transactional}: la transacción del procesador se confirma o se deshace de verdad. Por eso
 * guarda datos en la base y, al terminar cada test, borra solo las filas que creó (ids mayores a los
 * que había al empezar). Usar siempre una base desechable.
 *
 * <pre>
 * docker exec morosidad-local createdb -U morosidad sincronizacion_test
 * SINCRONIZACION_TEST_DB_URL=jdbc:postgresql://localhost:5434/sincronizacion_test \
 *   ./mvnw test -Dtest=SincronizacionRollbackPersistenciaTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "SINCRONIZACION_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {"sincronizacion.automatica.habilitada=false", "geopagos.username=test"})
class SincronizacionRollbackPersistenciaTest {

    static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");
    static final LocalDate DIA_1 = LocalDate.of(2026, 9, 24);
    static final LocalDate DIA_2 = DIA_1.plusDays(1);

    /** Tablas que escribe la sincronización, en orden de borrado (hijas primero). */
    static final List<String> TABLAS = List.of("contactos", "deuda", "padrones", "contribuyentes", "tributos");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("SINCRONIZACION_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> "morosidad");
        r.add("spring.datasource.password", () -> "morosidad-local");
    }

    static final RelojAjustable RELOJ = new RelojAjustable();

    @TestBean Clock clock;

    static Clock clock() {
        return RELOJ;
    }

    @Autowired SincronizacionDeudasProcessor processor;
    @Autowired SincronizacionDeudasService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoSpyBean SincronizacionContactosService contactos;
    @MockitoBean GeoPagosClient geoPagosClient;

    final JsonMapper json = JsonMapper.builder().build();
    final Map<String, Long> idMaximoInicial = new LinkedHashMap<>();

    @BeforeEach
    void recordarFilasExistentes() {
        RELOJ.mediodiaDe(DIA_1);
        for (String tabla : TABLAS) {
            idMaximoInicial.put(tabla, jdbc.queryForObject("SELECT coalesce(max(id), 0) FROM " + tabla, Long.class));
        }
    }

    @AfterEach
    void borrarFilasCreadas() {
        jdbc.update("DELETE FROM deuda_tributo WHERE deuda_id > ?", idMaximoInicial.get("deuda"));
        for (String tabla : TABLAS) {
            jdbc.update("DELETE FROM " + tabla + " WHERE id > ?", idMaximoInicial.get(tabla));
        }
    }

    @Test
    void siFallaAlFinalDelProcesadorNoQuedaNadaDeLaSincronizacion() {
        dia1();
        Map<String, List<Map<String, Object>>> antes = estadoDeLaBase();

        RELOJ.mediodiaDe(DIA_2);
        fallarDespuesDeEscribirContactos();

        // Día 2 trae una deuda nueva, dos actualizadas, una cancelada, cobros y contactos.
        assertThatThrownBy(() -> processor.procesar(pendientes("pendientes-dia2.json"), canceladas(),
                contribuyentes(), DIA_2, LocalDateTime.now(RELOJ), OrigenDatos.INTENDENCIA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falla simulada");

        assertThat(estadoDeLaBase()).isEqualTo(antes);
    }

    @Test
    void trasUnaFallaLaSiguienteSincronizacionSeAplicaCompleta() {
        dia1();
        Map<String, List<Map<String, Object>>> antes = estadoDeLaBase();
        RELOJ.mediodiaDe(DIA_2);
        respuestaDeLaApi("pendientes-dia2.json");
        fallarDespuesDeEscribirContactos();

        assertThatThrownBy(() -> service.sincronizar(OrigenDatos.INTENDENCIA, null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(estadoDeLaBase()).isEqualTo(antes);

        // La falla liberó el bloqueo de "sincronización en curso" y no dejó datos a medias.
        reset(contactos);
        ResultadoSincronizacion r = service.sincronizar(OrigenDatos.INTENDENCIA, null);

        assertThat(r.deudasCreadas()).isEqualTo(1);
        assertThat(r.deudasActualizadas()).isEqualTo(2);
        assertThat(r.deudasCanceladas()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deuda WHERE id > ?", Long.class,
                idMaximoInicial.get("deuda"))).isEqualTo(4);
    }

    // ================================================================ Utilidades

    /**
     * Deja que se escriban los contactos y fuerza el envío a la base antes de fallar, para que el
     * rollback tenga que deshacer filas que ya llegaron a PostgreSQL y no solo cambios en memoria.
     */
    void fallarDespuesDeEscribirContactos() {
        doAnswer(invocacion -> {
            invocacion.callRealMethod();
            if (TransactionSynchronizationManager.isActualTransactionActive()) em.flush();
            throw new IllegalStateException("falla simulada al terminar la sincronización");
        }).when(contactos).sincronizar(anyList());
    }

    void dia1() {
        processor.procesar(pendientes("pendientes-dia1.json"), List.of(), contribuyentes(), DIA_1,
                LocalDateTime.now(RELOJ), OrigenDatos.INTENDENCIA);
    }

    /** Todas las filas de las tablas de la sincronización, ordenadas, para comparar antes y después. */
    Map<String, List<Map<String, Object>>> estadoDeLaBase() {
        Map<String, List<Map<String, Object>>> estado = new LinkedHashMap<>();
        for (String tabla : TABLAS) {
            estado.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
        }
        estado.put("deuda_tributo", jdbc.queryForList("SELECT * FROM deuda_tributo ORDER BY deuda_id, tributo_id"));
        return estado;
    }

    void respuestaDeLaApi(String pendientes) {
        when(geoPagosClient.obtenerFacturasPendientes(any(), any()))
                .thenReturn(new IntendenciaApiResponse<>(pendientes(pendientes), null));
        when(geoPagosClient.obtenerFacturasCanceladas(any(), any()))
                .thenReturn(new IntendenciaApiResponse<>(canceladas(), null));
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(contribuyentes(), null));
    }

    List<GeoPagosFacturaPendienteDto> pendientes(String archivo) {
        return leer(archivo, new TypeReference<IntendenciaApiResponse<GeoPagosFacturaPendienteDto>>() {});
    }

    List<GeoPagosFacturaCanceladaDto> canceladas() {
        return leer("canceladas-dia2.json", new TypeReference<IntendenciaApiResponse<GeoPagosFacturaCanceladaDto>>() {});
    }

    List<GeoPagosContribuyenteDto> contribuyentes() {
        return leer("contribuyentes.json", new TypeReference<IntendenciaApiResponse<GeoPagosContribuyenteDto>>() {});
    }

    <T> List<T> leer(String archivo, TypeReference<IntendenciaApiResponse<T>> tipo) {
        try (InputStream in = getClass().getResourceAsStream("/sincronizacion/casos-reales/" + archivo)) {
            return json.readValue(in, tipo).getData();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Reloj fijo que el test puede mover de un día a otro. */
    static final class RelojAjustable extends Clock {
        private volatile Instant instante;

        void mediodiaDe(LocalDate dia) {
            instante = dia.atTime(12, 0).atZone(URUGUAY).toInstant();
        }

        @Override public ZoneId getZone() { return URUGUAY; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instante, zone); }
        @Override public Instant instant() { return instante; }
    }
}
