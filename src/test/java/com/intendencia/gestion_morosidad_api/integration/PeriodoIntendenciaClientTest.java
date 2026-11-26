package com.intendencia.gestion_morosidad_api.integration;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.*;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopat.client.GeoPatClient;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.time.LocalDate;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;
class PeriodoIntendenciaClientTest {
 @ParameterizedTest @EnumSource(OrigenDatos.class)
 void sinAnioOmiteFechaInicioYConservaConteoDesactivado(OrigenDatos origen){
  var b=RestClient.builder().baseUrl("https://test.invalid");var server=MockRestServiceServer.bindTo(b).build();var paginador=new IntendenciaPaginador(b.build(),1);
  IntendenciaClient client=origen==OrigenDatos.SUCIVE?new GeoPatClient(paginador):new GeoPagosClient(paginador);
  for(int i=1;i<=2;i++)server.expect(request -> assertThat(request.getURI().getQuery()).doesNotContain("fecha_inicio"))
    .andExpect(queryParam("fecha_corte","2026-11-11")).andExpect(queryParam("include_total","0")).andExpect(queryParam("page",String.valueOf(i)))
    .andRespond(withSuccess("{\"data\":[{\"CM\":"+i+"}],\"meta\":{\"total\":null,\"current_page\":"+i+",\"per_page\":1,\"last_page\":null,\"from\":"+i+",\"to\":"+i+",\"has_more\":"+(i<2)+"}}",MediaType.APPLICATION_JSON));
  assertThat(client.obtenerFacturasPendientes(null,LocalDate.of(2026,11,11)).getData()).hasSize(2);server.verify();
 }

 @ParameterizedTest @EnumSource(OrigenDatos.class)
 void pideUnaConfirmacionDelMismoOrigenCmYPeriodo(OrigenDatos origen) {
  var builder = RestClient.builder().baseUrl("https://test.invalid");
  var server = MockRestServiceServer.bindTo(builder).build();
  var paginador = new IntendenciaPaginador(builder.build(), 1000);
  IntendenciaClient client = origen == OrigenDatos.SUCIVE ? new GeoPatClient(paginador) : new GeoPagosClient(paginador);
  String ruta = "/api/v1/utec" + (origen == OrigenDatos.SUCIVE ? "/sucive" : "") + "/facturas/pendientes";
  server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo(ruta))
    .andExpect(queryParam("cm","100"))
    .andExpect(queryParam("fecha_inicio","2013-01-01"))
    .andExpect(queryParam("fecha_corte","2026-11-11"))
    .andExpect(queryParam("include_total","0"))
    .andRespond(withSuccess("""
      {"data":[],"meta":{"current_page":1,"per_page":1000,"from":null,"to":null,
                         "has_more":false,"total":null,"last_page":null}}
      """, MediaType.APPLICATION_JSON));
  assertThat(client.obtenerFacturasPendientesPorCm("100",LocalDate.of(2013,1,1),LocalDate.of(2026,11,11)).getData())
    .isEmpty();
  server.verify();
 }

 @ParameterizedTest @EnumSource(OrigenDatos.class)
 void laConfirmacionRechazaUnCmDistinto(OrigenDatos origen) {
  var builder = RestClient.builder().baseUrl("https://test.invalid");
  var server = MockRestServiceServer.bindTo(builder).build();
  var paginador = new IntendenciaPaginador(builder.build(), 1000);
  IntendenciaClient client = origen == OrigenDatos.SUCIVE ? new GeoPatClient(paginador) : new GeoPagosClient(paginador);
  server.expect(queryParam("cm","100")).andRespond(withSuccess("""
      {"data":[{"CM":101}],"meta":{"current_page":1,"per_page":1000,"from":1,"to":1,
                                  "has_more":false,"total":null,"last_page":null}}
      """, MediaType.APPLICATION_JSON));
  assertThatThrownBy(() -> client.obtenerFacturasPendientesPorCm("100",null,LocalDate.of(2026,11,11)))
    .isInstanceOf(com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException.class)
    .hasMessageContaining("CM distinto");
  server.verify();
 }

 @ParameterizedTest @EnumSource(OrigenDatos.class)
 void recorreTodasLasPaginasConElMismoPeriodo(OrigenDatos origen){
  var b=RestClient.builder().baseUrl("https://test.invalid");var server=MockRestServiceServer.bindTo(b).build();var paginador=new IntendenciaPaginador(b.build(),1);
  IntendenciaClient client=origen==OrigenDatos.SUCIVE?new GeoPatClient(paginador):new GeoPagosClient(paginador);
  String ruta="/api/v1/utec"+(origen==OrigenDatos.SUCIVE?"/sucive":"")+"/facturas/pendientes";
  for(int i=1;i<=2;i++)server.expect(requestTo(org.hamcrest.Matchers.containsString(ruta)))
    .andExpect(queryParam("fecha_inicio","2013-01-01")).andExpect(queryParam("fecha_corte","2026-11-11"))
    .andExpect(queryParam("include_total","0")).andExpect(queryParam("page",String.valueOf(i))).andRespond(withSuccess("{\"data\":[{\"CM\":"+i+"}],\"meta\":{\"total\":2,\"current_page\":"+i+",\"per_page\":1,\"last_page\":2,\"from\":"+i+",\"to\":"+i+",\"has_more\":"+(i<2)+"}}",MediaType.APPLICATION_JSON));
  assertThat(client.obtenerFacturasPendientes(LocalDate.of(2013,1,1),LocalDate.of(2026,11,11)).getData()).hasSize(2);server.verify();
 }
}
