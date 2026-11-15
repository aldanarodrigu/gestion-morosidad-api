package com.intendencia.gestion_morosidad_api.integration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.*;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import java.util.Map;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;
class IntendenciaSinTotalTest {
 String pagina(int pagina,int cm,boolean mas){return "{\"data\":[{\"CM\":"+cm+"}],\"meta\":{\"total\":null,\"last_page\":null,\"current_page\":"+pagina+",\"per_page\":1,\"from\":"+pagina+",\"to\":"+pagina+",\"has_more\":"+mas+"}}";}
 @Test void recorreHastaHasMoreFalseConTotalesNulos(){
  var b=RestClient.builder().baseUrl("https://test.invalid");var server=MockRestServiceServer.bindTo(b).build();var p=new IntendenciaPaginador(b.build(),1);
  server.expect(queryParam("include_total","0")).andRespond(withSuccess(pagina(1,1,true),MediaType.APPLICATION_JSON));
  server.expect(queryParam("page","2")).andRespond(withSuccess(pagina(2,2,false),MediaType.APPLICATION_JSON));
  var r=p.obtenerSinTotal("/pendientes",Map.of(),new ParameterizedTypeReference<IntendenciaApiResponse<GeoPagosFacturaPendienteDto>>(){},GeoPagosFacturaPendienteDto::cm);
  assertThat(r.getData()).hasSize(2);assertThat(r.getMeta().getTotal()).isEqualTo(2);server.verify();
 }
 @Test void reintentaLaMismaPaginaAnte504SinPerderNiDuplicarRegistros(){
  var b=RestClient.builder().baseUrl("https://test.invalid");var server=MockRestServiceServer.bindTo(b).build();var p=new IntendenciaPaginador(b.build(),1);
  server.expect(queryParam("page","1")).andRespond(withSuccess(pagina(1,1,true),MediaType.APPLICATION_JSON));
  server.expect(queryParam("page","2")).andRespond(withStatus(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT));
  server.expect(queryParam("page","2")).andRespond(withSuccess(pagina(2,2,false),MediaType.APPLICATION_JSON));
  var r=p.obtenerSinTotal("/pendientes",Map.of(),new ParameterizedTypeReference<IntendenciaApiResponse<GeoPagosFacturaPendienteDto>>(){},GeoPagosFacturaPendienteDto::cm);
  assertThat(r.getData()).hasSize(2);server.verify();
 }
 @Test void noReintentaErroresDeValidacion(){
  var b=RestClient.builder().baseUrl("https://test.invalid");var server=MockRestServiceServer.bindTo(b).build();var p=new IntendenciaPaginador(b.build(),1);
  server.expect(anything()).andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY));
  assertThatThrownBy(()->p.obtenerSinTotal("/pendientes",Map.of(),new ParameterizedTypeReference<IntendenciaApiResponse<GeoPagosFacturaPendienteDto>>(){},GeoPagosFacturaPendienteDto::cm)).isInstanceOf(org.springframework.web.client.RestClientResponseException.class);server.verify();
 }
 @Test void abandonaTrasTresErroresTransitorios(){
  var b=RestClient.builder().baseUrl("https://test.invalid");var server=MockRestServiceServer.bindTo(b).build();var p=new IntendenciaPaginador(b.build(),1);
  for(int i=0;i<3;i++)server.expect(anything()).andRespond(withStatus(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT));
  assertThatThrownBy(()->p.obtenerSinTotal("/pendientes",Map.of(),new ParameterizedTypeReference<IntendenciaApiResponse<GeoPagosFacturaPendienteDto>>(){},GeoPagosFacturaPendienteDto::cm)).isInstanceOf(org.springframework.web.client.RestClientResponseException.class);server.verify();
 }
 @ParameterizedTest @ValueSource(strings={"duplicado","paginaInvalida","limiteInvalido","faltaHasMore"})
 void rechazaDescargasInconsistentes(String caso){
  var b=RestClient.builder().baseUrl("https://test.invalid");var server=MockRestServiceServer.bindTo(b).build();var p=new IntendenciaPaginador(b.build(),1);
  server.expect(anything()).andRespond(withSuccess(pagina(1,1,true),MediaType.APPLICATION_JSON));
  String cuerpo=pagina(2,caso.equals("duplicado")?1:2,false);
  if(caso.equals("paginaInvalida"))cuerpo=cuerpo.replace("\"current_page\":2","\"current_page\":3");
  if(caso.equals("limiteInvalido"))cuerpo=cuerpo.replace("\"from\":2","\"from\":9");
  if(caso.equals("faltaHasMore"))cuerpo=cuerpo.replace("\"has_more\":false","\"has_more\":null");
  server.expect(anything()).andRespond(withSuccess(cuerpo,MediaType.APPLICATION_JSON));
  assertThatThrownBy(()->p.obtenerSinTotal("/pendientes",Map.of(),new ParameterizedTypeReference<IntendenciaApiResponse<GeoPagosFacturaPendienteDto>>(){},GeoPagosFacturaPendienteDto::cm)).isInstanceOf(com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException.class);
 }
}
