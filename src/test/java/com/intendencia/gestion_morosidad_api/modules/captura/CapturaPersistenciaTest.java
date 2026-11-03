package com.intendencia.gestion_morosidad_api.modules.captura;

import static com.intendencia.gestion_morosidad_api.modules.captura.CapturaDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.*;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named="CAPTURA_TEST_DB_URL",matches=".+")
@SpringBootTest(properties="sincronizacion.automatica.habilitada=false")
class CapturaPersistenciaTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("CAPTURA_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> System.getenv("CAPTURA_TEST_DB_USER"));
        r.add("spring.datasource.password", () -> System.getenv("CAPTURA_TEST_DB_PASSWORD"));
    }
    @Autowired CapturaService service;
    @Autowired PadronRepository padrones;
    @Autowired DeudaRepository deudas;
    @Autowired ContribuyenteRepository personas;
    Padron padron(String cm, OrigenDatos origen) {
        var c = new Contribuyente(); c.setNombre("Persona de prueba"); c.setDocumento("DOC-"+cm+origen); c=personas.save(c);
        var p = new Padron(); p.setCm(cm); p.setOrigen(origen); p.setNumeroPadron("00042"); p.setContribuyente(c);
        return padrones.save(p);
    }
    @Test void conservaCapturaTrasCambiarDatosActualesYDistingueProveedores() {
        var p = padron("10001",OrigenDatos.INTENDENCIA); padron("10001",OrigenDatos.SUCIVE);
        var d = new Deuda(); d.setPadron(p); d.setImporte(new BigDecimal("123.45")); d.setFechaSincronizacion(LocalDateTime.of(2026,10,30,12,0)); d=deudas.save(d);
        var c = service.crear(new Crear("  Primera  ",List.of(new Cuenta("10001",OrigenDatos.INTENDENCIA),new Cuenta("10001",OrigenDatos.SUCIVE))),"analista");
        assertEquals(2,c.cuentas().size()); assertEquals("Primera",c.nombre()); assertEquals("analista",c.usuario());
        d.setImporte(new BigDecimal("999.99")); deudas.save(d);
        p.setNumeroPadron("CAMBIADO"); padrones.save(p);
        var leida = service.obtener(c.id());
        var anterior = leida.cuentas().stream().filter(x -> x.origen()==OrigenDatos.INTENDENCIA).findFirst().orElseThrow();
        assertEquals("00042",anterior.numeroPadron()); assertEquals(0,new BigDecimal("123.45").compareTo(anterior.importe()));
        assertEquals(LocalDateTime.of(2026,10,30,12,0),anterior.fechaSincronizacion());
        assertTrue(leida.cuentas().stream().anyMatch(x -> x.origen()==OrigenDatos.SUCIVE && x.importe().signum()==0 && x.estado().equals("SIN_DEUDA_PENDIENTE")));
        var nueva=service.crear(new Crear("Segunda",List.of(new Cuenta("10001",OrigenDatos.INTENDENCIA))),"admin");
        assertEquals(0,new BigDecimal("999.99").compareTo(nueva.cuentas().getFirst().importe()));
        assertTrue(service.listar().stream().anyMatch(x -> x.id().equals(c.id()) && x.cantidadCuentas()==2));
    }
    @Test void cuentaCanceladaSeGuardaConSaldoCeroSinInferirPago() {
        var p=padron("10002",OrigenDatos.INTENDENCIA);
        var d=new Deuda(); d.setPadron(p); d.setEstado(EstadoDeuda.CANCELADA); d.setImporte(new BigDecimal("500")); deudas.save(d);
        var c=service.crear(new Crear("Cancelada",List.of(new Cuenta("10002",OrigenDatos.INTENDENCIA))),"usuario");
        assertEquals(0,c.cuentas().getFirst().importe().signum()); assertEquals("SIN_DEUDA_PENDIENTE",c.cuentas().getFirst().estado());
    }
    @Test void seleccionInvalidaNoDejaCapturasParciales() {
        padron("10003",OrigenDatos.INTENDENCIA);
        int cantidad=service.listar().size();
        assertThrows(RecursoNoEncontradoException.class,() -> service.crear(new Crear("Inválida",List.of(new Cuenta("10003",OrigenDatos.INTENDENCIA),new Cuenta("NO-EXISTE",OrigenDatos.SUCIVE))),"usuario"));
        assertEquals(cantidad,service.listar().size());
        assertThrows(ReglaNegocioException.class,() -> service.crear(new Crear("Duplicada",List.of(new Cuenta("10003",OrigenDatos.INTENDENCIA),new Cuenta(" 10003 ",OrigenDatos.INTENDENCIA))),"usuario"));
        assertEquals(cantidad,service.listar().size());
    }
    @Test void capturaInexistenteDaError() { assertThrows(RecursoNoEncontradoException.class,() -> service.obtener(-1L)); }
}
