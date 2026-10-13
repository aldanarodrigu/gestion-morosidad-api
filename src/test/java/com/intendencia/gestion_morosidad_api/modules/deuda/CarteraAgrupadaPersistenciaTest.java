package com.intendencia.gestion_morosidad_api.modules.deuda;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.*;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.SegmentoMoraRepository;
import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import com.intendencia.gestion_morosidad_api.modules.tributo.repository.TributoRepository;
import com.intendencia.gestion_morosidad_api.modules.accion.AccionRepository;
import com.intendencia.gestion_morosidad_api.modules.gestion.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/** Consulta real contra PostgreSQL desechable. Cada prueba se revierte y no usa la base de desarrollo. */
@EnabledIfEnvironmentVariable(named = "CARTERA_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {"sincronizacion.automatica.habilitada=false"})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "operador", roles = "NOTIFICADOR")
class CarteraAgrupadaPersistenciaTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("CARTERA_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> "morosidad");
        r.add("spring.datasource.password", () -> "morosidad-local");
    }
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired ContribuyenteRepository contribuyentes;
    @Autowired PadronRepository padrones;
    @Autowired DeudaRepository deudas;
    @Autowired TributoRepository tributos;
    @Autowired SegmentoMoraRepository segmentos;
    @Autowired AccionRepository acciones;
    @Autowired GestionRepository gestiones;
    @Autowired com.intendencia.gestion_morosidad_api.modules.contacto.repository.ContactoRepository contactos;
    int siguiente;

    void contacto(Contribuyente c, com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto tipo,
                  String valor, boolean propio) {
        var contacto = new com.intendencia.gestion_morosidad_api.modules.contacto.entity.Contacto();
        contacto.setContribuyente(c); contacto.setTipoContacto(tipo); contacto.setValor(valor);
        contacto.setOrigen("Prueba manual"); contacto.setEsDeContribuyente(propio); contactos.save(contacto);
    }

    Contribuyente persona(String nombre, String documento) {
        var c = new Contribuyente(); c.setNombre(nombre); c.setDocumento(documento);
        return contribuyentes.save(c);
    }
    Deuda deuda(Contribuyente c, String importe, int segmento, String fecha, Tributo... impuestos) {
        var p = new Padron(); p.setCm("TEST-" + ++siguiente); p.setNumeroPadron("P-" + siguiente);
        p.setContribuyente(c); p.setLocalidad("Localidad prueba"); padrones.save(p);
        var d = new Deuda(); d.setPadron(p); d.setImporte(new BigDecimal(importe));
        d.setEstado(EstadoDeuda.PENDIENTE); d.setDeudaDesde(LocalDate.parse(fecha));
        d.setSegmentoMora(segmentos.findAllByOrderByOrdenAsc().get(segmento - 1));
        d.getTributos().addAll(java.util.List.of(impuestos));
        return deudas.save(d);
    }
    ResultActions consultar(MockHttpServletRequestBuilder request) throws Exception {
        em.flush(); em.clear(); return mvc.perform(request).andExpect(status().isOk());
    }

    @Test void agrupaPorDocumentoAunqueCambieElNombreYSeparaHomonimos() throws Exception {
        var c = persona("Ana Alvarez", "90000001");
        deuda(c, "100", 1, "2026-10-01"); deuda(c, "200", 3, "2024-01-01");
        deuda(persona("Ana Alvarez", "90000002"), "500", 2, "2026-01-01");
        deuda(persona("Ana Otro nombre", " 90000001 "), "50", 2, "2026-01-01");
        consultar(get("/api/deudas/agrupadas"))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].documento").value("90000001"))
                .andExpect(jsonPath("$.contenido[0].nombres", containsInAnyOrder("Ana Alvarez", "Ana Otro nombre")))
                .andExpect(jsonPath("$.contenido[0].importeTotal").value(350))
                .andExpect(jsonPath("$.contenido[0].deudas.length()").value(3))
                .andExpect(jsonPath("$.contenido[0].segmentoMora.orden").value(3))
                .andExpect(jsonPath("$.contenido[1].documento").value("90000002"));
    }
    @Test void sinDocumentoUsaIdYNoUnePersonasPorNombre() throws Exception {
        var c = persona("Sin documento", null);
        deuda(c, "100", 1, "2026-01-01"); deuda(c, "200", 2, "2026-01-01");
        deuda(persona("Sin documento", ""), "50", 1, "2026-01-01");
        deuda(persona("Sin documento", "   "), "60", 1, "2026-01-01");
        consultar(get("/api/deudas/agrupadas"))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.contenido[?(@.clave == 'ID:" + c.getId() + "')].deudas[*].id", hasSize(2)));
    }
    @Test void paginaGruposCompletosYOrdenaPorSumaNoPorDeudaIndividual() throws Exception {
        var c = persona("A", "001");
        deuda(c, "100", 1, "2026-01-01"); deuda(c, "200", 3, "2025-01-01");
        deuda(persona("B", "002"), "250", 2, "2025-06-01");
        consultar(get("/api/deudas/agrupadas").param("size", "1").param("sort", "importe,desc"))
                .andExpect(jsonPath("$.totalElementos").value(2)).andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido[0].documento").value("001"))
                .andExpect(jsonPath("$.contenido[0].deudas.length()").value(2));
        consultar(get("/api/deudas/agrupadas").param("size", "1").param("page", "1").param("sort", "importe,desc"))
                .andExpect(jsonPath("$.contenido[0].documento").value("002"));
        consultar(get("/api/deudas/agrupadas").param("size", "1").param("page", "99"))
                .andExpect(jsonPath("$.totalElementos").value(2)).andExpect(jsonPath("$.contenido.length()").value(0));
    }
    @Test void filtraAntesDeAgruparSinDuplicarImportesPorTributos() throws Exception {
        var t = new Tributo(); t.setCodigo("TEST-1"); tributos.save(t);
        var otro = new Tributo(); otro.setCodigo("TEST-2"); tributos.save(otro);
        var c = persona("Nombre filtro", "001");
        var elegida = deuda(c, "150", 2, "2026-01-01", t, otro);
        deuda(c, "500", 3, "2024-01-01", t); deuda(c, "200", 2, "2026-01-01", otro);
        deuda(persona("Otra persona", "002"), "300", 2, "2026-01-01", t);
        consultar(get("/api/deudas/agrupadas").param("tributo", "TEST-1")
                        .param("segmento", elegida.getSegmentoMora().getId().toString()).param("contribuyente", "001"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].importeTotal").value(150))
                .andExpect(jsonPath("$.contenido[0].deudas.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].deudas[0].tributos.length()").value(2));
        consultar(get("/api/deudas/agrupadas").param("padron", elegida.getPadron().getNumeroPadron()).param("localidad", "prueba"))
                .andExpect(jsonPath("$.contenido[0].deudas.length()").value(1));
        consultar(get("/api/deudas/agrupadas").param("contribuyente", elegida.getPadron().getCm()))
                .andExpect(jsonPath("$.contenido[0].importeTotal").value(150));
    }
    @Test void excluyeCanceladasYSaldosNoPositivosComoLaCarteraActual() throws Exception {
        var c = persona("Prueba", "001");
        deuda(c, "100", 1, "2026-01-01");
        var cancelada = deuda(c, "200", 2, "2026-01-01"); cancelada.setEstado(EstadoDeuda.CANCELADA);
        deuda(c, "0", 1, "2026-01-01"); deuda(c, "-20", 1, "2026-01-01");
        consultar(get("/api/deudas/agrupadas"))
                .andExpect(jsonPath("$.contenido[0].importeTotal").value(100))
                .andExpect(jsonPath("$.contenido[0].deudas.length()").value(1));
        consultar(get("/api/deudas/agrupadas").param("estado", "CANCELADA"))
                .andExpect(jsonPath("$.contenido[0].importeTotal").value(200));
        consultar(get("/api/deudas")).andExpect(jsonPath("$.totalElementos").value(1));
    }
    @Test void conservaIdsDeCadaDeudaYCuentaSusGestionesSinMezclarHistorial() throws Exception {
        var c = persona("Persona", "001");
        var a = deuda(c, "100", 1, "2025-01-01"); var b = deuda(c, "200", 2, "2026-01-01");
        for (int i = 0; i < 2; i++) {
            var g = new Gestion(); g.setDeuda(a); g.setAccion(acciones.findAll().get(0));
            g.setFecha(LocalDateTime.now()); g.setResultado(ResultadoGestion.RESPONDIO); gestiones.save(g);
        }
        consultar(get("/api/deudas/agrupadas"))
                .andExpect(jsonPath("$.contenido[0].deudas[0].id").value(a.getId()))
                .andExpect(jsonPath("$.contenido[0].deudas[0].contribuyenteId").value(c.getId()))
                .andExpect(jsonPath("$.contenido[0].deudas[0].cantidadGestiones").value(2))
                .andExpect(jsonPath("$.contenido[0].deudas[1].id").value(b.getId()))
                .andExpect(jsonPath("$.contenido[0].deudas[1].cantidadGestiones").value(0));
        consultar(get("/api/gestiones/deuda/" + b.getId())).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void ordenPorNombreYAntiguedadEsEstable() throws Exception {
        deuda(persona("Zeta", "003"), "100", 1, "2024-01-01");
        deuda(persona("Alfa", "001"), "100", 1, "2026-01-01");
        deuda(persona("Alfa", "002"), "100", 1, "2026-01-01");
        consultar(get("/api/deudas/agrupadas").param("sort", "padron.contribuyente.nombre,asc"))
                .andExpect(jsonPath("$.contenido[*].documento", contains("001", "002", "003")));
        consultar(get("/api/deudas/agrupadas"))
                .andExpect(jsonPath("$.contenido[0].documento").value("003"));
    }
    @Test void conservaPermisosYValidaParametros() throws Exception {
        consultar(get("/api/deudas/agrupadas").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(jsonPath("$.totalElementos").value(0));
        consultar(get("/api/deudas/agrupadas").with(user("analista").roles("ANALISTA")))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(get("/api/deudas/agrupadas").param("estado", "INVALIDO")).andExpect(status().isBadRequest());
    }

    @Test void contactoFiltraAntesDePaginarSinDuplicarDeudasPorCadaContacto() throws Exception {
        var con = persona("Ana Alvarez", "001");
        var primera = deuda(con, "100", 1, "2025-01-01");
        var segunda = deuda(con, "200", 2, "2026-01-01");
        contacto(con, com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto.EMAIL, "ana@example.test", true);
        contacto(con, com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto.TELEFONO, "099123456", true);
        deuda(persona("Ana Alvarez", "002"), "400", 1, "2024-01-01");
        var vacio = persona("Contacto vacío", "003");
        deuda(vacio, "500", 1, "2024-01-01");
        contacto(vacio, com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto.EMAIL, " \t\r\n ".translateEscapes(), true);
        consultar(get("/api/deudas").param("conContacto", "true").param("size", "1"))
                .andExpect(jsonPath("$.totalElementos").value(2)).andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido[0].id").value(primera.getId()))
                .andExpect(jsonPath("$.contenido[0].conContacto").value(true));
        consultar(get("/api/deudas").param("conContacto", "true").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.contenido[0].id").value(segunda.getId()));
        consultar(get("/api/deudas").param("conContacto", "false"))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[*].contribuyente.documento", containsInAnyOrder("002", "003")))
                .andExpect(jsonPath("$.contenido[*].conContacto", everyItem(is(false))));
        consultar(get("/api/deudas")).andExpect(jsonPath("$.totalElementos").value(4));
    }

    @Test void contactoFiltraGruposCompletosConImportesYTotalesCorrectos() throws Exception {
        var con = persona("Ana Alvarez", "001");
        deuda(con, "100", 1, "2025-01-01"); deuda(con, "200", 2, "2026-01-01");
        contacto(con, com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto.EMAIL, "ana@example.test", true);
        contacto(con, com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto.TELEFONO, "099123456", true);
        deuda(persona("Ana Alvarez", "002"), "400", 1, "2024-01-01");
        var tercero = persona("Contacto tercero", "003");
        deuda(tercero, "50", 1, "2025-01-01");
        contacto(tercero, com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto.TELEFONO, "099987654", false);
        consultar(get("/api/deudas/agrupadas").param("conContacto", "true").param("size", "1").param("sort", "importe,desc"))
                .andExpect(jsonPath("$.totalElementos").value(2)).andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido[0].documento").value("001"))
                .andExpect(jsonPath("$.contenido[0].importeTotal").value(300))
                .andExpect(jsonPath("$.contenido[0].deudas.length()").value(2));
        consultar(get("/api/deudas/agrupadas").param("conContacto", "true").param("size", "1").param("page", "1").param("sort", "importe,desc"))
                .andExpect(jsonPath("$.contenido[0].documento").value("003"))
                .andExpect(jsonPath("$.contenido[0].importeTotal").value(50));
        consultar(get("/api/deudas/agrupadas").param("conContacto", "false"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].documento").value("002"))
                .andExpect(jsonPath("$.contenido[0].importeTotal").value(400));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto.class)
    void cualquierTipoYOrigenDeContactoConValorCuentaAunqueSeaDeUnTercero(
            com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto tipo) throws Exception {
        var c = persona("Contacto " + tipo, "001");
        deuda(c, "100", 1, "2025-01-01");
        contacto(c, tipo, "Dato registrado", false);
        for (String ruta : new String[]{"/api/deudas", "/api/deudas/agrupadas"}) {
            consultar(get(ruta).param("conContacto", "true")).andExpect(jsonPath("$.totalElementos").value(1));
            consultar(get(ruta).param("conContacto", "false")).andExpect(jsonPath("$.totalElementos").value(0));
        }
    }

    @Test void nuevoContactoGuardadoSeReflejaEnLaSiguienteConsultaDeCartera() throws Exception {
        var c = persona("Sin contacto inicial", "001");
        var d = deuda(c, "100", 1, "2025-01-01");
        consultar(get("/api/deudas").param("conContacto", "false")).andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/contribuyentes/" + d.getPadron().getCm() + "/contactos")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoContacto":"TELEFONO","valor":"099123456","origen":"Llamada","esDeContribuyente":true}
                                """))
                .andExpect(status().isOk());
        consultar(get("/api/deudas").param("conContacto", "true"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].conContacto").value(true));
        consultar(get("/api/deudas/agrupadas").param("conContacto", "false"))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test void contactoSeCombinaConBusquedaTributoYSegmentoEnAmbosListados() throws Exception {
        var t = new Tributo(); t.setCodigo("TEST-CONTACTO"); tributos.save(t);
        var con = persona("Ana Alvarez", "001");
        var elegida = deuda(con, "100", 2, "2026-01-01", t);
        deuda(con, "200", 3, "2024-01-01", t);
        contacto(con, com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto.EMAIL, "ana@example.test", true);
        deuda(persona("Ana Alvarez", "002"), "50", 2, "2026-01-01", t);
        for (String ruta : new String[]{"/api/deudas", "/api/deudas/agrupadas"}) {
            consultar(get(ruta).param("conContacto", "true").param("tributo", "TEST-CONTACTO")
                            .param("segmento", elegida.getSegmentoMora().getId().toString()).param("contribuyente", "Ana"))
                    .andExpect(jsonPath("$.totalElementos").value(1));
            consultar(get(ruta).param("conContacto", "false").param("tributo", "TEST-CONTACTO")
                            .param("segmento", elegida.getSegmentoMora().getId().toString()).param("contribuyente", "002"))
                    .andExpect(jsonPath("$.totalElementos").value(1));
            consultar(get(ruta).param("conContacto", "true").param("contribuyente", "002"))
                    .andExpect(jsonPath("$.totalElementos").value(0));
        }
    }

    @Test void contactoConValorDeFiltroInvalidoSeRechazaEnAmbosEndpoints() throws Exception {
        for (String ruta : new String[]{"/api/deudas", "/api/deudas/agrupadas"})
            mvc.perform(get(ruta).param("conContacto", "invalido")).andExpect(status().isBadRequest());
    }
}
