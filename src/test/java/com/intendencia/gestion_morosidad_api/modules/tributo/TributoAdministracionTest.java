package com.intendencia.gestion_morosidad_api.modules.tributo;

import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import com.intendencia.gestion_morosidad_api.modules.tributo.repository.TributoRepository;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "TRIBUTOS_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = "sincronizacion.automatica.habilitada=false")
@AutoConfigureMockMvc
@Transactional
class TributoAdministracionTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("TRIBUTOS_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> "morosidad");
        r.add("spring.datasource.password", () -> "morosidad-local");
    }
    @Autowired MockMvc mvc;
    @Autowired TributoRepository tributos;
    @Autowired ContribuyenteRepository contribuyentes;
    @Autowired PadronRepository padrones;
    @Autowired DeudaRepository deudas;
    @Autowired EntityManager em;
    Tributo tributo() {
        Tributo t = new Tributo(); t.setCodigo("000TEST"); return tributos.saveAndFlush(t);
    }
    @Test void creaYEditaDescripcionSinCambiarCodigo() throws Exception {
        mvc.perform(post("/api/tributos").with(user("mixto").roles("NOTIFICADOR", "ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\" 000TEST \",\"descripcion\":\" Inicial \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.codigo").value("000TEST"));
        em.flush(); em.clear();
        Tributo t = tributos.findByCodigo("000TEST").orElseThrow();
        mvc.perform(put("/api/tributos/" + t.getId()).with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\" Modificada \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("000TEST"));
        em.flush(); em.clear();
        assertThat(tributos.findById(t.getId()).orElseThrow().getDescripcion()).isEqualTo("Modificada");
    }
    @Test void eliminaTributoSinDeudas() throws Exception {
        Long id = tributo().getId();
        mvc.perform(delete("/api/tributos/" + id).with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isNoContent());
        em.clear(); assertThat(tributos.existsById(id)).isFalse();
    }
    @Test void noEliminaTributoAsociadoADeudasNiSusReferencias() throws Exception {
        Tributo t = tributo();
        Contribuyente c = new Contribuyente(); c.setNombre("Prueba tributos"); contribuyentes.saveAndFlush(c);
        Padron p = new Padron(); p.setCm("tributo-test"); p.setNumeroPadron("tributo-test"); p.setContribuyente(c); padrones.saveAndFlush(p);
        Deuda d = new Deuda(); d.setPadron(p); d.setImporte(BigDecimal.TEN); d.setTributos(Set.of(t)); deudas.saveAndFlush(d);
        mvc.perform(delete("/api/tributos/" + t.getId()).with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("No se puede eliminar un tributo asociado a deudas."));
        em.clear();
        assertThat(tributos.existsById(t.getId())).isTrue();
        assertThat(deudas.findById(d.getId()).orElseThrow().getTributos()).extracting(Tributo::getId).contains(t.getId());
    }
    @ParameterizedTest @ValueSource(strings = {"ANALISTA", "NOTIFICADOR"})
    void otrosRolesConsultanPeroNoAdministran(String rol) throws Exception {
        Long id = tributo().getId();
        mvc.perform(get("/api/tributos").with(user("operador").roles(rol))).andExpect(status().isOk());
        mvc.perform(post("/api/tributos").with(user("operador").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"nuevo\",\"descripcion\":\"Nueva\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/tributos/" + id).with(user("operador").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"Cambio\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/tributos/" + id).with(user("operador").roles(rol))).andExpect(status().isForbidden());
        assertThat(tributos.findById(id).orElseThrow().getDescripcion()).isNull();
    }
    @Test void duplicadoDevuelve409() throws Exception {
        tributo();
        mvc.perform(post("/api/tributos").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\" 000TEST \",\"descripcion\":\"Duplicado\"}"))
                .andExpect(status().isConflict());
    }
    @Test void rechazaCamposVaciosYLargos() throws Exception {
        for (String contenido : new String[]{"{}", "{\"codigo\":\" \",\"descripcion\":\" \"}",
                "{\"codigo\":\"" + "a".repeat(21) + "\",\"descripcion\":\"Valida\"}",
                "{\"codigo\":\"nuevo\",\"descripcion\":\"" + "a".repeat(201) + "\"}"}) {
            mvc.perform(post("/api/tributos").with(user("admin").roles("ADMINISTRADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(contenido)).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/tributos/1").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void inexistente404YSinSesion401() throws Exception {
        mvc.perform(delete("/api/tributos/99999999").with(user("admin").roles("ADMINISTRADOR"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/tributos/99999999").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"Nueva\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tributos")).andExpect(status().isUnauthorized());
    }
}
