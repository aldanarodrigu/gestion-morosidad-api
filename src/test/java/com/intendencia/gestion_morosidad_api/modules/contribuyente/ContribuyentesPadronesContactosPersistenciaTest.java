package com.intendencia.gestion_morosidad_api.modules.contribuyente;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.*;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.repository.ContactoRepository;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Datos ficticios en una base desechable: nunca consulta proveedores externos. */
@EnabledIfEnvironmentVariable(named="CONTACTOS_TEST_DB_URL",matches=".+")
@SpringBootTest(properties="sincronizacion.automatica.habilitada=false")
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username="operador",roles="NOTIFICADOR")
class ContribuyentesPadronesContactosPersistenciaTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getenv("CONTACTOS_TEST_DB_URL"));
        r.add("spring.datasource.username",()->"morosidad");
        r.add("spring.datasource.password",()->"morosidad-local");
    }
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired ContribuyenteRepository personas;
    @Autowired PadronRepository padrones;
    @Autowired DeudaRepository deudas;
    @Autowired ContactoRepository contactos;
    @Autowired SincronizacionContactosService sincronizacion;
    Contribuyente ana,homonima;
    Padron primero,segundo,sucive;
    @BeforeEach void preparar() {
        ana=persona("Ana Pérez","00123");homonima=persona("Ana Pérez","00999");
        primero=padron(ana,"100","0007","Libertad",OrigenDatos.INTENDENCIA);
        segundo=padron(ana,"101","0008","Libertad",OrigenDatos.INTENDENCIA);
        sucive=padron(homonima,"100","0007","San José",OrigenDatos.SUCIVE);
        for(var p:List.of(primero,segundo,sucive)) {
            var d=new Deuda();d.setPadron(p);d.setImporte(BigDecimal.TEN);deudas.save(d);
        }
        em.flush();
    }
    Contribuyente persona(String nombre,String documento) {
        var c=new Contribuyente();c.setNombre(nombre);c.setDocumento(documento);return personas.saveAndFlush(c);
    }
    Padron padron(Contribuyente c,String cm,String numero,String localidad,OrigenDatos origen) {
        var p=new Padron();p.setContribuyente(c);p.setCm(cm);p.setNumeroPadron(numero);p.setLocalidad(localidad);p.setOrigen(origen);
        return padrones.saveAndFlush(p);
    }
    String cuerpo(Long id,TipoContacto tipo,String valor,String fuente) {
        return "{\"id\":"+id+",\"tipoContacto\":\""+tipo+"\",\"valor\":\""+valor+"\",\"esDeContribuyente\":false,\"origen\":\""+fuente+"\"}";
    }
    long guardar(String cm,String origen,Long id,TipoContacto tipo,String valor,String fuente,String rol) throws Exception {
        var r=mvc.perform(put("/api/contribuyentes/"+cm+"/contactos").param("origen",origen)
                .with(user("usuario").roles(rol)).contentType("application/json").content(cuerpo(id,tipo,valor,fuente)))
                .andExpect(status().isOk()).andReturn();
        em.flush();em.clear();Number n=com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(),"$.id");return n.longValue();
    }
    @Test void mismoCmResuelveContribuyentesDistintosSegunOrigen() throws Exception {
        mvc.perform(get("/api/contribuyentes/100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("00123")).andExpect(jsonPath("$.origen").value("INTENDENCIA"));
        mvc.perform(get("/api/contribuyentes/100").param("origen","SUCIVE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("00999")).andExpect(jsonPath("$.origen").value("SUCIVE"));
    }
    @Test void padronesPorCmNoUnenHomonimosNiPierdenCerosIniciales() throws Exception {
        mvc.perform(get("/api/contribuyentes/100/padrones")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].numeroPadron").value("0007")).andExpect(jsonPath("$[1].numeroPadron").value("0008"));
        mvc.perform(get("/api/contribuyentes/100/padrones").param("origen","SUCIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contribuyente.documento").value("00999"));
    }
    @Test void numeroDePadronRepetidoDevuelveTodosYLocalidadSeleccionaElCorrecto() throws Exception {
        mvc.perform(get("/api/padrones/0007")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/padrones/0007/contribuyente").param("localidad","  libertad "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].documento").value("00123"));
        mvc.perform(get("/api/padrones/0007").param("localidad","NO EXISTE")).andExpect(status().isNotFound());
    }
    @Test void listadoFiltraAntesDePaginarYConservaUnaFilaPorPadron() throws Exception {
        for(int pagina=0;pagina<2;pagina++)
            mvc.perform(get("/api/contribuyentes").param("nombre"," ANA ").param("documento","00123")
                    .param("size","1").param("page",Integer.toString(pagina)).param("sort","id,asc"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2))
                    .andExpect(jsonPath("$.totalPaginas").value(2)).andExpect(jsonPath("$.contenido[0].cm").value(pagina==0?"100":"101"));
        mvc.perform(get("/api/contribuyentes").param("documento","NO EXISTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
    }
    @ParameterizedTest @ValueSource(strings={"cancelada","fueraPeriodo","sinDeuda"})
    void listadoExcluyePadronesSinDeudaVigente(String caso) throws Exception {
        var d=deudas.findByPadronCmAndPadronOrigen("100",OrigenDatos.SUCIVE).orElseThrow();
        if(caso.equals("cancelada"))d.setEstado(EstadoDeuda.CANCELADA);
        if(caso.equals("fueraPeriodo"))d.setEnPeriodoGestion(false);
        if(caso.equals("sinDeuda"))deudas.delete(d);
        em.flush();em.clear();
        mvc.perform(get("/api/contribuyentes")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2));
    }
    @ParameterizedTest @ValueSource(strings={"ADMINISTRADOR","NOTIFICADOR","ANALISTA"})
    void todosLosRolesCreanEditanYCompartenContactosEntrePadronesDeLaMismaPersona(String rol) throws Exception {
        long id=guardar("100","INTENDENCIA",null,TipoContacto.TELEFONO," 099123456 "," Declaración ",rol);
        mvc.perform(get("/api/contribuyentes/101/contactos").with(user("lector").roles(rol)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].valor").value("099123456")).andExpect(jsonPath("$[0].origen").value("Declaración"));
        long editado=guardar("101","INTENDENCIA",id,TipoContacto.EMAIL,"nuevo@example.test","Entrevista",rol);
        assertThat(editado).isEqualTo(id);assertThat(contactos.count()).isEqualTo(1);
        mvc.perform(get("/api/contribuyentes/100/contactos")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipoContacto").value("EMAIL")).andExpect(jsonPath("$[0].valor").value("nuevo@example.test"));
    }
    @Test void noPermiteEditarContactosDeOtroTitularAunqueCoincidaElCm() throws Exception {
        long id=guardar("100","INTENDENCIA",null,TipoContacto.EMAIL,"antes@example.test","Manual","NOTIFICADOR");
        mvc.perform(put("/api/contribuyentes/100/contactos").param("origen","SUCIVE").contentType("application/json")
                .content(cuerpo(id,TipoContacto.EMAIL,"ajeno@example.test","Manual"))).andExpect(status().isNotFound());
        em.clear();assertThat(contactos.findById(id).orElseThrow().getValor()).isEqualTo("antes@example.test");
        mvc.perform(get("/api/contribuyentes/100/contactos").param("origen","SUCIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @ParameterizedTest @EnumSource(TipoContacto.class)
    void guardaTodosLosTiposConLosLimitesDe255Caracteres(TipoContacto tipo) throws Exception {
        long id=guardar("100","SUCIVE",null,tipo,"x".repeat(255),"f".repeat(255),"ANALISTA");
        var c=contactos.findById(id).orElseThrow();assertThat(c.getValor()).hasSize(255);assertThat(c.getOrigen()).hasSize(255);
        assertThat(c.getContribuyente().getId()).isEqualTo(homonima.getId());assertThat(c.getEsDeContribuyente()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"valorLargo","fuenteLarga","valorVacio","fuenteVacia","tipoDesconocido","idCero"})
    void datosInvalidosNoCreanContactos(String caso) throws Exception {
        String json=cuerpo(null,TipoContacto.EMAIL,"a@example.test","Manual");
        switch(caso) {
            case "valorLargo" -> json=cuerpo(null,TipoContacto.EMAIL,"a".repeat(256),"Manual");
            case "fuenteLarga" -> json=cuerpo(null,TipoContacto.EMAIL,"a@example.test","f".repeat(256));
            case "valorVacio" -> json=cuerpo(null,TipoContacto.EMAIL,"  ","Manual");
            case "fuenteVacia" -> json=cuerpo(null,TipoContacto.EMAIL,"a@example.test","  ");
            case "tipoDesconocido" -> json=json.replace("EMAIL","INVALIDO");
            case "idCero" -> json=cuerpo(0L,TipoContacto.EMAIL,"a@example.test","Manual");
        }
        mvc.perform(put("/api/contribuyentes/100/contactos").contentType("application/json").content(json)).andExpect(status().isBadRequest());
        assertThat(contactos.count()).isZero();
    }
    @Test void sincronizacionConservaUnContactoImportadoQueFueEditado() throws Exception {
        long id=guardar("100","INTENDENCIA",null,TipoContacto.EMAIL,"corregido@example.test",SincronizacionContactosService.PERSONAS,"ADMINISTRADOR");
        sincronizacion.sincronizar(List.of(new SincronizacionContactosService.Dato(personas.findById(ana.getId()).orElseThrow(),
                TipoContacto.EMAIL,SincronizacionContactosService.PERSONAS,"externo@example.test",true)));
        em.flush();em.clear();assertThat(contactos.count()).isEqualTo(1);
        assertThat(contactos.findById(id).orElseThrow().getValor()).isEqualTo("corregido@example.test");
    }
    @Test void sinSesionNoPuedeConsultarNiEditarYNoSeInsertaNada() throws Exception {
        mvc.perform(get("/api/contribuyentes/100/contactos").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/contribuyentes/100/contactos").with(anonymous()).contentType("application/json")
                .content(cuerpo(null,TipoContacto.EMAIL,"a@example.test","Manual"))).andExpect(status().isUnauthorized());
        assertThat(contactos.count()).isZero();
    }
    @Test void padronInexistenteDevuelve404SinCrearContactos() throws Exception {
        for(String ruta:List.of("/api/contribuyentes/NO-EXISTE","/api/contribuyentes/NO-EXISTE/padrones","/api/contribuyentes/NO-EXISTE/contactos","/api/padrones/NO-EXISTE"))
            mvc.perform(get(ruta)).andExpect(status().isNotFound());
        mvc.perform(put("/api/contribuyentes/NO-EXISTE/contactos").contentType("application/json")
                .content(cuerpo(null,TipoContacto.EMAIL,"a@example.test","Manual"))).andExpect(status().isNotFound());
        assertThat(contactos.count()).isZero();
    }

    @Test void usuarioAutenticadoSinRolDelSistemaNoAccede() throws Exception {
        var ajeno=user("ajeno").roles("USER");
        for(String ruta:List.of("/api/contribuyentes","/api/contribuyentes/100","/api/contribuyentes/100/contactos","/api/padrones/0007"))
            mvc.perform(get(ruta).with(ajeno)).andExpect(status().isForbidden());
        mvc.perform(put("/api/contribuyentes/100/contactos").with(ajeno).contentType("application/json")
                .content(cuerpo(null,TipoContacto.EMAIL,"a@example.test","Manual"))).andExpect(status().isForbidden());
        assertThat(contactos.count()).isZero();
    }
}
