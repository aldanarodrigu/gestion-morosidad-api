package com.intendencia.gestion_morosidad_api.modules.accion;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.SegmentoMoraRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Alta, baja y errores del catálogo de acciones contra PostgreSQL real.
 * Sin @Transactional a propósito: las restricciones de la base (FK) se verifican al confirmar la
 * transacción del servicio, como en producción; una transacción de prueba que nunca confirma las ocultaría.
 */
@EnabledIfEnvironmentVariable(named = "ACCIONES_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = "sincronizacion.automatica.habilitada=false")
@AutoConfigureMockMvc
class AccionAdministracionTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("ACCIONES_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> "morosidad");
        r.add("spring.datasource.password", () -> "morosidad-local");
    }
    @Autowired MockMvc mvc;
    @Autowired AccionRepository acciones;
    @Autowired SegmentoMoraRepository segmentos;
    @Autowired ContribuyenteRepository contribuyentes;
    @Autowired PadronRepository padrones;
    @Autowired DeudaRepository deudas;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void limpiar() {
        jdbc.update("DELETE FROM gestiones WHERE accion_id IN (SELECT id FROM acciones WHERE nombre ILIKE 'QA %')");
        jdbc.update("DELETE FROM acciones_segmentos WHERE accion_id IN (SELECT id FROM acciones WHERE nombre ILIKE 'QA %')");
        jdbc.update("DELETE FROM acciones WHERE nombre ILIKE 'QA %'");
        jdbc.update("DELETE FROM deuda WHERE padron_id IN (SELECT id FROM padrones WHERE cm = 'qa-accion')");
        jdbc.update("DELETE FROM padrones WHERE cm = 'qa-accion'");
        jdbc.update("DELETE FROM contribuyentes WHERE nombre = 'QA acciones'");
    }

    Long segmentoId() { return segmentos.findAllByOrderByOrdenAsc().getFirst().getId(); }

    Long crearPorApi(String nombre) throws Exception {
        var respuesta = mvc.perform(post("/api/acciones").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + nombre + "\",\"segmentoId\":" + segmentoId() + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    void registrarGestion(Long accionId) {
        Contribuyente c = new Contribuyente(); c.setNombre("QA acciones"); contribuyentes.saveAndFlush(c);
        Padron p = new Padron(); p.setCm("qa-accion"); p.setNumeroPadron("qa-accion"); p.setContribuyente(c); padrones.saveAndFlush(p);
        Deuda d = new Deuda(); d.setPadron(p); d.setImporte(BigDecimal.TEN); deudas.saveAndFlush(d);
        jdbc.update("INSERT INTO gestiones (fecha, resultado, deuda_id, accion_id) VALUES (now(), 'EXITOSA', ?, ?)", d.getId(), accionId);
    }

    @Test void creaAccionConNombreRecortadoYSuSegmento() throws Exception {
        mvc.perform(post("/api/acciones").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  QA Llamada  \",\"segmentoId\":" + segmentoId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("QA Llamada"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.segmentos[0].id").value(segmentoId()));
    }

    @Test void nombreDuplicadoSinImportarMayusculasNiEspaciosDevuelve409() throws Exception {
        crearPorApi("QA Visita");

        mvc.perform(post("/api/acciones").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" qa visita \",\"segmentoId\":" + segmentoId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acciones WHERE nombre ILIKE 'QA Visita'", Long.class)).isEqualTo(1);
    }

    @Test void segmentoInexistenteDevuelve404SinCrearLaAccion() throws Exception {
        mvc.perform(post("/api/acciones").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"QA Sin segmento\",\"segmentoId\":99999999}"))
                .andExpect(status().isNotFound());
        assertThat(acciones.existsByNombreIgnoreCase("QA Sin segmento")).isFalse();
    }

    @Test void rechazaCamposVaciosLargosOSegmentoInvalido() throws Exception {
        for (String contenido : new String[]{"{}", "{\"nombre\":\" \",\"segmentoId\":1}",
                "{\"nombre\":\"QA " + "a".repeat(98) + "\",\"segmentoId\":1}",
                "{\"nombre\":\"QA Valida\"}", "{\"nombre\":\"QA Valida\",\"segmentoId\":0}"}) {
            mvc.perform(post("/api/acciones").with(user("admin").roles("ADMINISTRADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(contenido))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores").isMap());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acciones WHERE nombre ILIKE 'QA %'", Long.class)).isZero();
    }

    @Test void inexistenteDevuelve404AlConsultarModificarOEliminar() throws Exception {
        mvc.perform(get("/api/acciones/99999999").with(user("admin").roles("ADMINISTRADOR"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/acciones/99999999").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/acciones/99999999").with(user("admin").roles("ADMINISTRADOR"))).andExpect(status().isNotFound());
    }

    @Test void desactivaYReactivaSinPerderElSegmento() throws Exception {
        Long id = crearPorApi("QA Carta");

        mvc.perform(put("/api/acciones/" + id).with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.segmentos[0].id").value(segmentoId()));
        mvc.perform(put("/api/acciones/" + id).with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
    }

    @Test void eliminaAccionSinGestionesJuntoConSuVinculoAlSegmento() throws Exception {
        Long id = crearPorApi("QA Borrable");

        mvc.perform(delete("/api/acciones/" + id).with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isNoContent());
        assertThat(acciones.existsById(id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acciones_segmentos WHERE accion_id = ?", Long.class, id)).isZero();
    }

    @Test void noEliminaAccionConGestionesYConservaElHistorial() throws Exception {
        Long id = crearPorApi("QA Usada");
        registrarGestion(id);

        mvc.perform(delete("/api/acciones/" + id).with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No se puede eliminar una acción con gestiones registradas; desactivala para dejar de ofrecerla."));
        assertThat(acciones.existsById(id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gestiones WHERE accion_id = ?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acciones_segmentos WHERE accion_id = ?", Long.class, id)).isEqualTo(1);
    }
}
