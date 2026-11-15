package com.intendencia.gestion_morosidad_api.modules.convenio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class ConvenioServiceTest {
 MockRestServiceServer server; ConvenioService service;
 @BeforeEach void setup() { var b = RestClient.builder().baseUrl("https://test.invalid"); server = MockRestServiceServer.bindTo(b).build(); service = new ConvenioService(b.build()); }
 @ParameterizedTest @EnumSource(OrigenDatos.class)
 void consultaOrigenFiltrosYConservaDecimales(OrigenDatos origen) {
  server.expect(requestTo(org.hamcrest.Matchers.containsString(origen == OrigenDatos.SUCIVE ? "/sucive/convenios/pendientes" : "/utec/convenios/pendientes")))
   .andExpect(queryParam("page","2")).andExpect(queryParam("per_page","20")).andExpect(queryParam("cm","900"))
   .andExpect(queryParam("documento","123")).andExpect(queryParam("nombre","Ana"))
   .andExpect(queryParam("fecha_inicio","2026-01-01")).andExpect(queryParam("fecha_corte","2026-02-01"))
   .andRespond(withSuccess("""
    {"data":[{"CM":900,"NUMERO_CONVENIO":123,"PERSONA":null,"IMPORTE_DEUDA":"123.45000","CANT_CUOTAS_VENC":2,"CANT_CUOTAS_CONVENIOS":12}],
    "meta":{"total":21,"current_page":2,"per_page":20,"last_page":2,"has_more":false}}
    """,MediaType.APPLICATION_JSON));
  var r = service.consultar(origen,2,20,900L," 123 "," Ana ",LocalDate.of(2026,1,1),LocalDate.of(2026,2,1));
  assertThat(r.getData().getFirst().IMPORTE_DEUDA()).isEqualTo("123.45000");
  assertThat(r.getData().getFirst().NUMERO_CONVENIO()).isEqualTo("123"); server.verify();
 }
 @Test void rechazaParametrosAntesDeConsultar() {
  assertThatThrownBy(() -> service.consultar(OrigenDatos.INTENDENCIA,0,20,null,null,null,null,null)).isInstanceOf(ReglaNegocioException.class);
  assertThatThrownBy(() -> service.consultar(OrigenDatos.INTENDENCIA,1,101,null,null,null,null,null)).isInstanceOf(ReglaNegocioException.class);
  assertThatThrownBy(() -> service.consultar(OrigenDatos.INTENDENCIA,1,20,-1L,null,null,null,null)).isInstanceOf(ReglaNegocioException.class);
  assertThatThrownBy(() -> service.consultar(OrigenDatos.INTENDENCIA,1,20,null,null,null,LocalDate.of(2026,2,1),LocalDate.of(2026,1,1))).isInstanceOf(ReglaNegocioException.class);
  server.verify();
 }
 @Test void respuestaVaciaValida() {
  server.expect(anything()).andRespond(withSuccess("{\"data\":[],\"meta\":{\"total\":0,\"current_page\":1,\"per_page\":20,\"last_page\":1,\"has_more\":false}}",MediaType.APPLICATION_JSON));
  assertThat(service.consultar(OrigenDatos.INTENDENCIA,1,20,null,null,null,null,null).getData()).isEmpty();
 }
 @Test void rechazaRespuestaIncompleta() {
  server.expect(anything()).andRespond(withSuccess("{\"data\":[]}",MediaType.APPLICATION_JSON));
  assertThatThrownBy(() -> service.consultar(OrigenDatos.INTENDENCIA,1,20,null,null,null,null,null)).isInstanceOf(IntegracionExternaException.class);
 }
 @Test void comunicaErrorExterno() {
  server.expect(anything()).andRespond(withStatus(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT));
  assertThatThrownBy(() -> service.consultar(OrigenDatos.INTENDENCIA,1,20,null,null,null,null,null)).isInstanceOf(IntegracionExternaException.class).hasMessageContaining("504");
 }
}
