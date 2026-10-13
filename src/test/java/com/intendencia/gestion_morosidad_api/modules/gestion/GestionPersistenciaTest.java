package com.intendencia.gestion_morosidad_api.modules.gestion;

import com.intendencia.gestion_morosidad_api.modules.accion.*;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.usuario.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Solo se ejecuta con una base PostgreSQL desechable explícita; nunca usa la base de desarrollo.
@EnabledIfEnvironmentVariable(named = "GESTIONES_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {"sincronizacion.automatica.habilitada=false"})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "gestion-test", roles = "NOTIFICADOR")
class GestionPersistenciaTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("GESTIONES_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> "morosidad");
        r.add("spring.datasource.password", () -> "morosidad-local");
    }
    @Autowired MockMvc mvc;
    @Autowired GestionRepository gestiones;
    @Autowired DeudaRepository deudas;
    @Autowired PadronRepository padrones;
    @Autowired ContribuyenteRepository contribuyentes;
    @Autowired AccionRepository acciones;
    @Autowired UsuarioRepository usuarios;
    @Autowired com.intendencia.gestion_morosidad_api.modules.segmento.repository.SegmentoMoraRepository segmentos;
    @Autowired com.intendencia.gestion_morosidad_api.modules.accion_segmento.AccionSegmentoRepository vinculos;
    @Autowired jakarta.persistence.EntityManager em;
    Long deudaId;
    Long otraDeudaId;
    Long accionId;
    @BeforeEach void datos() {
        Usuario u = new Usuario(); u.setUsername("gestion-test"); u.setPassword("no-login"); u.setRoles(java.util.Set.of(Rol.NOTIFICADOR)); usuarios.saveAndFlush(u);
        Contribuyente c = new Contribuyente(); c.setNombre("Prueba gestiones"); c.setDocumento("prueba"); contribuyentes.saveAndFlush(c);
        deudaId = deuda(c, "test-1"); otraDeudaId = deuda(c, "test-2");
        Accion a = new Accion(); a.setNombre("Acción de prueba integración"); accionId = acciones.saveAndFlush(a).getId();
    }
    Long deuda(Contribuyente c, String cm) {
        Padron p = new Padron(); p.setCm(cm); p.setNumeroPadron(cm); p.setContribuyente(c); padrones.saveAndFlush(p);
        Deuda d = new Deuda(); d.setPadron(p); d.setImporte(BigDecimal.TEN); return deudas.saveAndFlush(d).getId();
    }
    void registrar(Long id, String nota) throws Exception {
        mvc.perform(post("/api/gestiones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deudaId\":" + id + ",\"accionId\":" + accionId + ",\"resultado\":\"EXITOSA\",\"observacion\":\"" + nota + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioNombre").value("gestion-test"));
    }
    @Test void persisteMultiplesGestionesYAislaHistorialEntreDeudas() throws Exception {
        registrar(deudaId, "primera"); registrar(deudaId, "segunda"); registrar(otraDeudaId, "otra deuda");
        gestiones.flush();
        mvc.perform(get("/api/gestiones/deuda/" + deudaId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].observacion").value("segunda"))
                .andExpect(jsonPath("$[1].observacion").value("primera"));
        assertThat(gestiones.findByDeuda_IdOrderByFechaDescIdDesc(deudaId)).hasSize(2);
        mvc.perform(get("/api/contribuyentes/id/" + deudas.findById(deudaId).orElseThrow().getPadron().getContribuyente().getId() + "/deudas"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].cantidadGestiones").value(2))
                .andExpect(jsonPath("$[1].cantidadGestiones").value(1));
    }
    @Test void accionInactivaNoProduceInsert() throws Exception {
        Accion a = acciones.findById(accionId).orElseThrow(); a.setActivo(false); acciones.flush();
        mvc.perform(post("/api/gestiones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deudaId\":" + deudaId + ",\"accionId\":" + accionId + ",\"resultado\":\"PENDIENTE\"}"))
                .andExpect(status().isBadRequest());
        assertThat(gestiones.findByDeuda_IdOrderByFechaDescIdDesc(deudaId)).isEmpty();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RESPONDIO", "NO_RESPONDIO", "PROXIMO_A_VENIR"})
    void persisteNuevosResultadosYNotificadorPuedeGestionar(String resultado) throws Exception {
        mvc.perform(post("/api/gestiones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deudaId\":" + deudaId + ",\"accionId\":" + accionId + ",\"resultado\":\"" + resultado + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.resultado").value(resultado));
        assertThat(gestiones.findByDeuda_IdOrderByFechaDescIdDesc(deudaId)).singleElement()
                .satisfies(g -> assertThat(g.getResultado().name()).isEqualTo(resultado));
    }
    @Test @org.springframework.security.test.context.support.WithAnonymousUser
    void noPermiteRegistrarSinSesion() throws Exception {
        mvc.perform(post("/api/gestiones").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(gestiones.findByDeuda_IdOrderByFechaDescIdDesc(deudaId)).isEmpty();
    }
    @Test void deudaSinSegmentoRespondeSinAcciones() throws Exception {
        mvc.perform(get("/api/gestiones/deuda/" + deudaId + "/acciones-disponibles"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void creaAccionConMoraYApareceSoloEnEseSegmento() throws Exception {
        var catalogo = segmentos.findAllByOrderByOrdenAsc();
        var mora = catalogo.get(0);
        deudas.findById(deudaId).orElseThrow().setSegmentoMora(mora);
        deudas.findById(otraDeudaId).orElseThrow().setSegmentoMora(catalogo.get(1));
        deudas.flush();
        var respuesta = mvc.perform(post("/api/acciones")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("mixto").roles("ADMINISTRADOR", "NOTIFICADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" Nueva accion de prueba \",\"segmentoId\":" + mora.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.nombre").value("Nueva accion de prueba"))
                .andExpect(jsonPath("$.segmentos[0].id").value(mora.getId())).andReturn();
        Number id = com.jayway.jsonpath.JsonPath.read(respuesta.getResponse().getContentAsString(), "$.id");
        acciones.flush(); em.clear();
        assertThat(vinculos.existsByAccion_IdAndSegmentoMora_Id(id.longValue(), mora.getId())).isTrue();
        mvc.perform(get("/api/gestiones/deuda/" + deudaId + "/acciones-disponibles"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.hasItem(id.intValue())));
        mvc.perform(get("/api/gestiones/deuda/" + otraDeudaId + "/acciones-disponibles"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id.intValue()))));
        mvc.perform(get("/api/acciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + id + ")].segmentos[0].nombre", org.hamcrest.Matchers.hasItem(mora.getNombre())));
        mvc.perform(post("/api/gestiones").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deudaId\":" + deudaId + ",\"accionId\":" + id + ",\"resultado\":\"EXITOSA\"}"))
                .andExpect(status().isCreated());
    }
    @Test void accionConSegmentoInexistenteNoSeGuarda() throws Exception {
        long antes = acciones.count();
        mvc.perform(post("/api/acciones")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"No debe crearse\",\"segmentoId\":99999999}"))
                .andExpect(status().isNotFound());
        assertThat(acciones.count()).isEqualTo(antes);
    }
    @Test void rechazaNombreDuplicadoOSinSegmentoSinCrearVinculos() throws Exception {
        Long segmentoId = segmentos.findAllByOrderByOrdenAsc().get(0).getId();
        String nombre = acciones.findById(accionId).orElseThrow().getNombre();
        long antes = acciones.count(); long asociaciones = vinculos.count();
        mvc.perform(post("/api/acciones")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\" " + nombre.toUpperCase() + " \",\"segmentoId\":" + segmentoId + "}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/acciones")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Sin segmento\"}"))
                .andExpect(status().isBadRequest());
        assertThat(acciones.count()).isEqualTo(antes);
        assertThat(vinculos.count()).isEqualTo(asociaciones);
    }

}
