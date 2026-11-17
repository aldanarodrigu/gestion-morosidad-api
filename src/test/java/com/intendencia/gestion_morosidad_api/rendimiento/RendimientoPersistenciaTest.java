package com.intendencia.gestion_morosidad_api.rendimiento;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaCanceladaDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.SegmentoMoraRepository;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.service.SincronizacionDeudasService;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendimiento con el volumen real de GeoPagos (unas 22.600 deudas y 71.800 contribuyentes) sobre
 * PostgreSQL desechable, con el pool de producción (3 conexiones) y el batching de JDBC.
 * Los datos son sintéticos pero con la forma de la API: claves en mayúsculas, personas con varios
 * padrones, el mismo número de padrón en varias localidades y documentos vacíos.
 *
 * Mide la sincronización a partir de las respuestas ya descargadas: la descarga desde GeoPagos
 * depende de la red y de la Intendencia y se mide aparte, en QA, con el resumen de la sincronización.
 * Escribe el informe en target/rendimiento/informe.md.
 *
 * <pre>
 * docker exec morosidad-local createdb -U morosidad rendimiento_test
 * RENDIMIENTO_TEST_DB_URL=jdbc:postgresql://localhost:5434/rendimiento_test \
 *   ./mvnw test -Dtest=RendimientoPersistenciaTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "RENDIMIENTO_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {"sincronizacion.automatica.habilitada=false", "geopagos.username=test"})
@AutoConfigureMockMvc
class RendimientoPersistenciaTest {

    static final int DEUDAS = 22_600;
    static final int CONTRIBUYENTES = 71_800;
    static final int PERSONAS = 17_000;
    static final int COBROS = 3_000;

    /** Criterio de salida del plan de testing [Supuesto]: sincronización completa en menos de 2 minutos. */
    static final Duration LIMITE_SINCRONIZACION = Duration.ofMinutes(2);
    /** Una página de cartera debe responder en menos de 1 s (p95) [Supuesto]. */
    static final Duration LIMITE_PAGINA = Duration.ofSeconds(1);
    /** El reporte resume toda la cartera [Supuesto]. */
    static final Duration LIMITE_REPORTE = Duration.ofSeconds(5);
    /**
     * Exportar escribe un Excel con toda la cartera (22.600 filas): es una descarga, no una pantalla
     * [Supuesto, a confirmar con la Intendencia]. Medido: unos 8 s, mitad en la base y mitad armando el archivo.
     */
    static final Duration LIMITE_EXPORTACION = Duration.ofSeconds(15);
    static final int REPETICIONES = 10;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        String url = System.getenv("RENDIMIENTO_TEST_DB_URL");
        // Igual que producción: inserts en lote reescritos por el driver.
        r.add("spring.datasource.url", () -> url.contains("reWriteBatchedInserts") ? url
                : url + (url.contains("?") ? "&" : "?") + "reWriteBatchedInserts=true");
        r.add("spring.datasource.username", () -> "morosidad");
        r.add("spring.datasource.password", () -> "morosidad-local");
    }

    @Autowired SincronizacionDeudasService sincronizacion;
    @Autowired SegmentoMoraRepository segmentos;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean GeoPagosClient geoPagosClient;

    final JsonMapper json = JsonMapper.builder().build();
    final LocalDate hoy = LocalDate.now();
    final StringBuilder informe = new StringBuilder();
    final List<String> excedidos = new ArrayList<>();

    static final String[] APELLIDOS = {"GONZALEZ", "RODRIGUEZ", "FERNANDEZ", "LOPEZ", "MARTINEZ", "PEREZ", "GARCIA",
            "SANCHEZ", "ROMERO", "SOSA", "SILVA", "TORRES", "PEREIRA", "DIAZ", "ALVAREZ", "SUAREZ", "BENITEZ", "CABRERA"};
    static final String[] NOMBRES = {"MARIA", "JUAN", "ANA", "CARLOS", "LAURA", "JOSE", "LUCIA", "DIEGO", "SOFIA", "PABLO"};
    static final String[] LOCALIDADES = {"MALDONADO", "SAN CARLOS", "PIRIAPOLIS", "PUNTA DEL ESTE", "PAN DE AZUCAR",
            "AIGUA", "GARZON", "SOLIS GRANDE"};
    static final String[] TIPOS = {"CIU", "COM", "RUR", "SUB"};

    @Test
    void sincronizacionYConsultasConVolumenReal() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deuda", Long.class))
                .as("La base de rendimiento debe estar vacía: recrearla antes de cada corrida").isZero();
        informe.append("# Informe de rendimiento\n\n")
                .append("Fecha: ").append(hoy).append(". Volumen: ").append(DEUDAS).append(" deudas, ")
                .append(CONTRIBUYENTES).append(" registros de contribuyentes, ").append(COBROS).append(" cobros. ")
                .append("Pool de ").append(jdbc.queryForObject("SHOW max_connections", String.class))
                .append(" conexiones máximas en el servidor; la aplicación usa DB_POOL_SIZE=3.\n\n");

        medirSincronizaciones();
        medirConsultas();

        Path archivo = Path.of("target", "rendimiento", "informe.md");
        Files.createDirectories(archivo.getParent());
        Files.writeString(archivo, informe);
        System.out.println(informe);
        assertThat(excedidos).as("Mediciones por encima del límite").isEmpty();
    }

    // ============================================================ Sincronización

    void medirSincronizaciones() {
        var pendientesDia1 = pendientes(0);
        var contribuyentes = contribuyentes();
        informe.append("## Sincronización\n\n| Corrida | Tiempo | Recibidas | Creadas | Actualizadas | Canceladas | Omitidas |\n")
                .append("|---|---|---|---|---|---|---|\n");

        var primera = sincronizar("Primera carga (base vacía)", pendientesDia1, List.of(), contribuyentes);
        assertThat(primera.deudasCreadas()).isEqualTo(DEUDAS);

        // Día siguiente: 3 % se pagó (desaparece), 10 % cambió de importe y entran 1 % de deudas nuevas.
        var pendientesDia2 = pendientes(1);
        var segunda = sincronizar("Sincronización diaria", pendientesDia2, cobros(), contribuyentes);
        assertThat(segunda.deudasCanceladas()).isPositive();

        var tercera = sincronizar("Sin cambios", pendientesDia2, List.of(), contribuyentes);
        assertThat(tercera.deudasCreadas()).isZero();
        informe.append("\nLímite: ").append(formato(LIMITE_SINCRONIZACION)).append(" por corrida.\n\n");
    }

    ResultadoSincronizacion sincronizar(String nombre, List<GeoPagosFacturaPendienteDto> pendientes,
                                        List<GeoPagosFacturaCanceladaDto> cobros, List<GeoPagosContribuyenteDto> contribuyentes) {
        when(geoPagosClient.obtenerFacturasPendientes(any(), any())).thenReturn(new IntendenciaApiResponse<>(pendientes, null));
        when(geoPagosClient.obtenerFacturasCanceladas(any(), any())).thenReturn(new IntendenciaApiResponse<>(cobros, null));
        when(geoPagosClient.obtenerContribuyentes()).thenReturn(new IntendenciaApiResponse<>(contribuyentes, null));

        long inicio = System.nanoTime();
        var resultado = sincronizacion.sincronizar(OrigenDatos.INTENDENCIA, null);
        Duration tiempo = Duration.ofNanos(System.nanoTime() - inicio);

        informe.append("| ").append(nombre).append(" | ").append(formato(tiempo)).append(" | ")
                .append(resultado.pendientesRecibidas()).append(" | ").append(resultado.deudasCreadas()).append(" | ")
                .append(resultado.deudasActualizadas()).append(" | ").append(resultado.deudasCanceladas()).append(" | ")
                .append(resultado.registrosOmitidos()).append(" |\n");
        if (tiempo.compareTo(LIMITE_SINCRONIZACION) > 0) excedidos.add(nombre + ": " + formato(tiempo));
        return resultado;
    }

    // ============================================================ Consultas

    void medirConsultas() throws Exception {
        long tardia = segmentos.findAllByOrderByOrdenAsc().getLast().getId();
        int ultimaPagina = DEUDAS / 100 - 1;
        informe.append("## Consultas (").append(REPETICIONES).append(" repeticiones, tras 2 de calentamiento)\n\n")
                .append("| Consulta | p50 | p95 | Máx | Límite |\n|---|---|---|---|---|\n");

        Map<String, String> paginas = new LinkedHashMap<>();
        paginas.put("Cartera: primera página", "/api/deudas");
        paginas.put("Cartera: 100 por página, última página", "/api/deudas?size=100&page=" + ultimaPagina);
        paginas.put("Cartera: estado + segmento + tributo", "/api/deudas?estado=PENDIENTE&segmento=" + tardia + "&tributo=101");
        paginas.put("Cartera: búsqueda por nombre", "/api/deudas?contribuyente=gonzalez");
        paginas.put("Cartera: con contacto + localidad, por importe", "/api/deudas?conContacto=true&localidad=maldonado&sort=importe,desc");
        paginas.put("Cartera agrupada: primera página", "/api/deudas/agrupadas");
        paginas.put("Cartera agrupada: búsqueda, 100 por página", "/api/deudas/agrupadas?contribuyente=gonzalez&size=100");
        paginas.put("Cartera agrupada: segmento, última página", "/api/deudas/agrupadas?segmento=" + tardia + "&size=100&page=40");
        paginas.put("Contribuyentes: búsqueda por nombre", "/api/contribuyentes?nombre=rodriguez");
        paginas.put("Tributos: catálogo paginado", "/api/tributos/paginados?busqueda=1");
        paginas.forEach((nombre, ruta) -> medir(nombre, ruta, LIMITE_PAGINA));

        medir("Reporte de cartera", "/api/reportes/cartera", LIMITE_REPORTE);
        medir("Exportar cartera a Excel", "/api/deudas/exportar", LIMITE_EXPORTACION);
        informe.append('\n');
    }

    void medir(String nombre, String ruta, Duration limite) {
        try {
            for (int i = 0; i < 2; i++) ejecutar(ruta);
            long[] tiempos = new long[REPETICIONES];
            for (int i = 0; i < REPETICIONES; i++) {
                long inicio = System.nanoTime();
                ejecutar(ruta);
                tiempos[i] = System.nanoTime() - inicio;
            }
            Arrays.sort(tiempos);
            Duration p50 = Duration.ofNanos(tiempos[REPETICIONES / 2]);
            Duration p95 = Duration.ofNanos(tiempos[(int) Math.ceil(REPETICIONES * 0.95) - 1]);
            Duration max = Duration.ofNanos(tiempos[REPETICIONES - 1]);
            informe.append("| ").append(nombre).append(" | ").append(formato(p50)).append(" | ").append(formato(p95))
                    .append(" | ").append(formato(max)).append(" | ").append(formato(limite)).append(" |\n");
            if (p95.compareTo(limite) > 0) excedidos.add(nombre + ": p95 " + formato(p95));
        } catch (Exception ex) {
            throw new IllegalStateException(nombre + " falló", ex);
        }
    }

    void ejecutar(String ruta) throws Exception {
        mvc.perform(get(ruta).with(user("rendimiento").roles("ADMINISTRADOR"))).andExpect(status().isOk());
    }

    static String formato(Duration d) {
        return d.toMillis() >= 1000 ? String.format("%.1f s", d.toMillis() / 1000.0) : d.toMillis() + " ms";
    }

    // ============================================================ Datos sintéticos

    /** Pendientes del día: el día 1 tiene todas; el día 2 pierde un 3 %, cambia un 10 % y suma un 1 %. */
    List<GeoPagosFacturaPendienteDto> pendientes(int dia) {
        Random azar = new Random(42);
        List<Map<String, Object>> filas = new ArrayList<>();
        int total = dia == 0 ? DEUDAS : DEUDAS + DEUDAS / 100;
        for (int i = 0; i < total; i++) {
            Map<String, Object> fila = pendiente(i, azar);
            if (dia == 1) {
                if (i % 33 == 0) continue;
                if (i % 10 == 1) fila.put("IMPORTE_DEUDA", ((java.math.BigDecimal) fila.get("IMPORTE_DEUDA")).add(java.math.BigDecimal.TEN));
            } else if (i >= DEUDAS) continue;
            filas.add(fila);
        }
        return convertir(filas, new TypeReference<>() {});
    }

    Map<String, Object> pendiente(int i, Random azar) {
        int persona = i % PERSONAS;
        LocalDate desde = hoy.minusDays(1 + azar.nextInt(4_000));
        LocalDate ultimo = desde.plusDays(azar.nextInt((int) Math.max(1, hoy.toEpochDay() - desde.toEpochDay())));
        if (!ultimo.isBefore(hoy)) ultimo = hoy.minusDays(1);
        int tributos = 1 + azar.nextInt(3);
        StringBuilder codigos = new StringBuilder();
        for (int t = 0; t < tributos; t++) codigos.append(t == 0 ? "" : ", ").append(100 + azar.nextInt(40));
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("CM", 2_000_000 + i);
        fila.put("NUMERO_PADRON", String.valueOf(1 + i % 9_000));
        fila.put("PADTIPO", TIPOS[i % TIPOS.length]);
        fila.put("LOCALIDAD", LOCALIDADES[(i / 9_000 + i) % LOCALIDADES.length]);
        fila.put("PERSONA", nombre(persona));
        fila.put("DOCUMENTO", persona % 33 == 0 ? "" : documento(persona));
        fila.put("TELEFONO", persona % 5 < 3 ? "09" + (1_000_000 + persona) : null);
        fila.put("EMAIL", persona % 4 == 0 ? "persona" + persona + "@correo.test" : "");
        fila.put("DIRECCION", "CALLE " + (persona % 300));
        fila.put("CONVENIO", i % 25 == 0 ? "SI" : "NO");
        fila.put("TRIBUTOS_DEUDA", codigos.toString());
        java.math.BigDecimal importe = java.math.BigDecimal.valueOf(100 + azar.nextInt(200_000), 0).add(new java.math.BigDecimal("0.50"));
        fila.put("IMPORTE_DEUDA", importe);
        fila.put("DEUDA_FACTURAS", importe);
        fila.put("SALDO_A_FAVOR", "0.00");
        fila.put("DEUDA_DESDE", desde.toString());
        fila.put("ULTIMO_VENCIMIENTO", ultimo.toString());
        fila.put("ANIO_DESDE", desde.getYear());
        fila.put("ANIO_HASTA", ultimo.getYear());
        fila.put("CANT_FACT_VENC", 1 + azar.nextInt(60));
        return fila;
    }

    List<GeoPagosFacturaCanceladaDto> cobros() {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (int i = 0; i < COBROS; i++) {
            int cm = (i * 7) % DEUDAS;
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("CM", 2_000_000 + cm);
            fila.put("NUMERO_PADRON", String.valueOf(1 + cm % 9_000));
            fila.put("PERSONA", nombre(cm % PERSONAS));
            fila.put("DOCUMENTO", documento(cm % PERSONAS));
            fila.put("NUMERO_COBRO", 9_000_000 + i);
            fila.put("FECHA_COBRO", hoy.minusDays(i % 2).toString());
            fila.put("IMPORTE_TOTAL", "1500.00");
            filas.add(fila);
        }
        return convertir(filas, new TypeReference<>() {});
    }

    /** Contactos de Personas y GeoPagos: incluye CM sin deuda, como la API real. */
    List<GeoPagosContribuyenteDto> contribuyentes() {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (int i = 0; i < CONTRIBUYENTES; i++) {
            int persona = i % PERSONAS;
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("CM", 2_000_000 + i);
            fila.put("NUMERO_PADRON", String.valueOf(1 + i % 9_000));
            fila.put("TIPO_PADRON", TIPOS[i % TIPOS.length]);
            fila.put("LOCALIDAD", LOCALIDADES[(i / 9_000 + i) % LOCALIDADES.length]);
            fila.put("NOMBRE_PERSONAS", nombre(persona));
            fila.put("TELEFONO_PERSONAS", persona % 3 == 0 ? "42" + (100_000 + persona) : null);
            fila.put("EMAIL_PERSONAS", persona % 7 == 0 ? "contacto" + persona + "@correo.test" : null);
            fila.put("NOMBRE_GEOPAGOS", i % 6 == 0 ? nombre(persona) : null);
            fila.put("TELEFONO_GEOPAGOS", i % 6 == 0 ? "098" + (100_000 + persona) : null);
            filas.add(fila);
        }
        return convertir(filas, new TypeReference<>() {});
    }

    static String nombre(int persona) {
        return APELLIDOS[persona % APELLIDOS.length] + " " + APELLIDOS[(persona / 7) % APELLIDOS.length] + ", "
                + NOMBRES[(persona / 3) % NOMBRES.length];
    }

    static String documento(int persona) {
        return String.format("%d.%03d.%03d-%d", 1 + persona / 1_000_000, (persona / 1_000) % 1_000, persona % 1_000, persona % 10);
    }

    <T> List<T> convertir(List<Map<String, Object>> filas, TypeReference<List<T>> tipo) {
        return json.convertValue(filas, tipo);
    }
}
