package com.intendencia.gestion_morosidad_api.modules.deuda.service;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class HistorialPadronServiceTest {
 DeudaRepository repo=mock(DeudaRepository.class); MockRestServiceServer server; HistorialPadronService service;
 @BeforeEach void preparar(){var b=RestClient.builder().baseUrl("https://test.invalid");server=MockRestServiceServer.bindTo(b).build();service=new HistorialPadronService(repo,b.build());}
 void deuda(OrigenDatos origen){var p=new Padron();p.setCm("1001");p.setOrigen(origen);var d=new Deuda();d.setPadron(p);when(repo.findById(42L)).thenReturn(Optional.of(d));}
 @ParameterizedTest @EnumSource(OrigenDatos.class)
 void consultaFuenteCorrectaYConservaRelacionesYPadronesDeOrigen(OrigenDatos origen){
  deuda(origen);String pref=origen==OrigenDatos.SUCIVE ? "/sucive" : "";
  server.expect(requestTo("https://test.invalid/api/v1/utec"+pref+"/contribuyentes/historial?cm=1001"))
   .andRespond(withSuccess("""
   {"data":[{"CM_PADRON":1001,"NRO_PADRON":"0007","NOMBRE":"Ana","DOCUMENTO":"00123","DESCRIPCION_RELACION":"Propietario","SITUACION_REGISTRO":"ACTIVO"},
   {"CM_PADRON":900,"NRO_PADRON":7,"NOMBRE":"Ana","DOCUMENTO":"00123","DESCRIPCION_RELACION":"Otra relacion","SITUACION_REGISTRO":"NO ACTIVO - SOLO HISTORICO","EMAIL":null}],"meta":{"total":2}}
   """,MediaType.APPLICATION_JSON));
  var r=service.consultar(42L);assertEquals(2,r.data().size());assertEquals("0007",r.data().getFirst().numeroPadron());assertEquals("00123",r.data().getFirst().documento());assertEquals(900L,r.data().get(1).cmPadron());assertNull(r.data().get(1).email());server.verify();verify(repo,never()).save(any());
 }
 @Test void listaVaciaValida(){deuda(OrigenDatos.INTENDENCIA);server.expect(anything()).andRespond(withSuccess("{\"data\":[],\"meta\":{\"total\":0}}",MediaType.APPLICATION_JSON));assertTrue(service.consultar(42L).data().isEmpty());}
 @Test void deudaInexistenteNoConsultaFuente(){assertThrows(RecursoNoEncontradoException.class,()->service.consultar(42L));server.verify();}
 @ParameterizedTest @ValueSource(strings={"{}","{\"data\":[],\"meta\":{\"total\":1}}","{\"data\":[null],\"meta\":{\"total\":1}}"})
 void rechazaHistorialIncompleto(String body){deuda(OrigenDatos.INTENDENCIA);server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));assertThrows(IntegracionExternaException.class,()->service.consultar(42L));}
 @Test void comunicaErrorSinExponerDetalle(){deuda(OrigenDatos.SUCIVE);server.expect(anything()).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT).body("informacion interna"));var e=assertThrows(IntegracionExternaException.class,()->service.consultar(42L));assertTrue(e.getMessage().contains("504"));assertFalse(e.getMessage().contains("interna"));}
}
