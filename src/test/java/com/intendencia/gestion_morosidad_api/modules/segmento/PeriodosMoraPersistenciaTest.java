package com.intendencia.gestion_morosidad_api.modules.segmento;

import com.intendencia.gestion_morosidad_api.modules.segmento.repository.*;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.*;
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
import java.time.Clock;
import java.time.LocalDate;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "PERIODOS_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = "sincronizacion.automatica.habilitada=false")
@AutoConfigureMockMvc
@Transactional
class PeriodosMoraPersistenciaTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("PERIODOS_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> "morosidad");
        r.add("spring.datasource.password", () -> "morosidad-local");
    }
    @Autowired MockMvc mvc;
    @Autowired SegmentoMoraRepository segmentos;
    @Autowired ConfiguracionSegmentoRepository rangos;
    @Autowired ContribuyenteRepository contribuyentes;
    @Autowired PadronRepository padrones;
    @Autowired DeudaRepository deudas;
    @Autowired EntityManager em;
    @Autowired Clock clock;
    @Autowired com.intendencia.gestion_morosidad_api.modules.tributo.repository.TributoRepository tributos;
    String cuerpo(int finTemprana, int finInicial) {
        var s = segmentos.findAllByOrderByOrdenAsc();
        return """
                {"rangos":[
                {"segmentoId":%d,"diasDesde":0,"diasHasta":%d},
                {"segmentoId":%d,"diasDesde":%d,"diasHasta":%d},
                {"segmentoId":%d,"diasDesde":%d,"diasHasta":null}]}
                """.formatted(s.get(0).getId(), finTemprana, s.get(1).getId(), finTemprana + 1,
                        finInicial, s.get(2).getId(), finInicial + 1);
    }
    Long deuda(int dias, EstadoDeuda estado) {
        var s = segmentos.findAllByOrderByOrdenAsc();
        Contribuyente c = new Contribuyente(); c.setNombre("Prueba periodos"); contribuyentes.saveAndFlush(c);
        Padron p = new Padron(); p.setCm("periodos-" + dias); p.setNumeroPadron("periodos-" + dias); p.setContribuyente(c); padrones.saveAndFlush(p);
        Deuda d = new Deuda(); d.setPadron(p); d.setImporte(BigDecimal.TEN);
        d.setDeudaDesde(LocalDate.now(clock).minusDays(dias)); d.setEstado(estado);
        d.setSegmentoMora(s.get(dias > 365 ? 2 : dias > 90 ? 1 : 0));
        return deudas.saveAndFlush(d).getId();
    }
    @Test void guardaTodosLosRangosConHistorialYReclasificaDeudasActivas() throws Exception {
        Long primera = deuda(110, EstadoDeuda.PENDIENTE);
        Long segunda = deuda(390, EstadoDeuda.EN_GESTION);
        Long cancelada = deuda(400, EstadoDeuda.CANCELADA);
        long cantidadAntes = rangos.count();
        var s = segmentos.findAllByOrderByOrdenAsc();
        mvc.perform(put("/api/configuraciones-segmento").with(user("mixto").roles("NOTIFICADOR", "ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(120, 450)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].diasHasta").value(120))
                .andExpect(jsonPath("$[1].diasDesde").value(121)).andExpect(jsonPath("$[2].diasDesde").value(451));
        em.flush(); em.clear();
        assertThat(rangos.count()).isEqualTo(cantidadAntes + 3);
        assertThat(rangos.findByActivaTrueOrderByDiasDesdeAsc()).hasSize(3);
        assertThat(deudas.findById(primera).orElseThrow().getSegmentoMora().getId()).isEqualTo(s.get(0).getId());
        assertThat(deudas.findById(segunda).orElseThrow().getSegmentoMora().getId()).isEqualTo(s.get(1).getId());
        assertThat(deudas.findById(cancelada).orElseThrow().getSegmentoMora().getId()).isEqualTo(s.get(2).getId());
    }
    @ParameterizedTest @ValueSource(strings = {"hueco", "solapamiento", "invertido", "negativo", "sinfin", "vacio", "duplicado"})
    void rechazaCambiosInvalidosSinModificarRangos(String caso) throws Exception {
        String contenido = cuerpo(90, 365);
        switch (caso) {
            case "hueco" -> contenido = contenido.replace("\"diasDesde\":91", "\"diasDesde\":92");
            case "solapamiento" -> contenido = contenido.replace("\"diasDesde\":91", "\"diasDesde\":90");
            case "invertido" -> contenido = cuerpo(120, 100);
            case "negativo" -> contenido = cuerpo(-1, 365);
            case "sinfin" -> contenido = contenido.replace("\"diasHasta\":null", "\"diasHasta\":999");
            case "vacio" -> contenido = "{\"rangos\":[]}";
            case "duplicado" -> contenido = contenido.replace("\"segmentoId\":2", "\"segmentoId\":1");
        }
        long antes = rangos.count();
        mvc.perform(put("/api/configuraciones-segmento").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(contenido)).andExpect(status().isBadRequest());
        assertThat(rangos.count()).isEqualTo(antes);
        assertThat(rangos.findByActivaTrueOrderByDiasDesdeAsc()).extracting(c -> c.getDiasDesde()).containsExactly(0, 91, 366);
    }
    @ParameterizedTest @ValueSource(strings = {"ANALISTA", "NOTIFICADOR"})
    void otrosRolesSoloConsultan(String rol) throws Exception {
        mvc.perform(get("/api/configuraciones-segmento").with(user("operador").roles(rol))).andExpect(status().isOk());
        mvc.perform(put("/api/configuraciones-segmento").with(user("operador").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(120, 450))).andExpect(status().isForbidden());
    }
    @Test void sinSesion401() throws Exception {
        mvc.perform(put("/api/configuraciones-segmento").contentType(MediaType.APPLICATION_JSON).content(cuerpo(120, 450)))
                .andExpect(status().isUnauthorized());
    }
    com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo tributo(String codigo) {
        var t = new com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo();
        t.setCodigo(codigo); return tributos.saveAndFlush(t);
    }
    void guardarTributo(Long id, int temprana, int inicial) throws Exception {
        String contenido = cuerpo(temprana, inicial).replace("{\"rangos\"", "{\"tributoId\":" + id + ",\"rangos\"");
        mvc.perform(put("/api/configuraciones-segmento").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].tributoId").value(id));
    }
    @Test void rangosPorTributoNoModificanLaBaseNiOtrosTributos() throws Exception {
        var a = tributo("tributo-a"); var b = tributo("tributo-b");
        mvc.perform(get("/api/configuraciones-segmento").param("tributoId", a.getId().toString())
                        .with(user("analista").roles("ANALISTA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].diasHasta").value(90))
                .andExpect(jsonPath("$[0].tributoId").isEmpty());
        guardarTributo(a.getId(), 10, 20);
        guardarTributo(b.getId(), 150, 500);
        mvc.perform(get("/api/configuraciones-segmento").with(user("analista").roles("ANALISTA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].diasHasta").value(90));
        mvc.perform(get("/api/configuraciones-segmento").param("tributoId", a.getId().toString())
                        .with(user("analista").roles("ANALISTA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].diasHasta").value(10));
        mvc.perform(get("/api/configuraciones-segmento").param("tributoId", b.getId().toString())
                        .with(user("analista").roles("ANALISTA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].diasHasta").value(150));
    }
    @Test void deudaConVariosTributosTomaElMasAvanzadoYRestaurarBaseReclasifica() throws Exception {
        var a = tributo("tributo-a"); var b = tributo("tributo-b"); var c = tributo("tributo-c");
        Long mixta = deuda(110, EstadoDeuda.PENDIENTE);
        Long suave = deuda(111, EstadoDeuda.PENDIENTE);
        Long heredada = deuda(112, EstadoDeuda.PENDIENTE);
        deudas.findById(mixta).orElseThrow().setTributos(new java.util.HashSet<>(java.util.List.of(a, b)));
        deudas.findById(suave).orElseThrow().setTributos(new java.util.HashSet<>(java.util.List.of(b)));
        deudas.findById(heredada).orElseThrow().setTributos(new java.util.HashSet<>(java.util.List.of(b, c)));
        deudas.flush();
        guardarTributo(a.getId(), 10, 20);
        guardarTributo(b.getId(), 150, 500);
        em.flush(); em.clear();
        var s = segmentos.findAllByOrderByOrdenAsc();
        assertThat(deudas.findById(mixta).orElseThrow().getSegmentoMora().getId()).isEqualTo(s.get(2).getId());
        assertThat(deudas.findById(suave).orElseThrow().getSegmentoMora().getId()).isEqualTo(s.get(0).getId());
        assertThat(deudas.findById(heredada).orElseThrow().getSegmentoMora().getId()).isEqualTo(s.get(1).getId());
        mvc.perform(delete("/api/configuraciones-segmento/tributo/" + a.getId()).with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isNoContent());
        em.flush(); em.clear();
        assertThat(deudas.findById(mixta).orElseThrow().getSegmentoMora().getId()).isEqualTo(s.get(1).getId());
        mvc.perform(get("/api/configuraciones-segmento").param("tributoId", a.getId().toString()).param("historial", "true")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].activa").value(false));
    }
    @Test void configuracionInvalidaDeTributoNoModificaLaBaseYProtegeHistorial() throws Exception {
        var a = tributo("tributo-a");
        String invalida = cuerpo(120, 100).replace("{\"rangos\"", "{\"tributoId\":" + a.getId() + ",\"rangos\"");
        long antes = rangos.count();
        mvc.perform(put("/api/configuraciones-segmento").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalida)).andExpect(status().isBadRequest());
        assertThat(rangos.count()).isEqualTo(antes);
        guardarTributo(a.getId(), 10, 20);
        mvc.perform(delete("/api/tributos/" + a.getId()).with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isBadRequest());
        assertThat(tributos.existsById(a.getId())).isTrue();
    }

}
