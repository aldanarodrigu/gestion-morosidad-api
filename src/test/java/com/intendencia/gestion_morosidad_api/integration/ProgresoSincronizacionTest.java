package com.intendencia.gestion_morosidad_api.integration;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.*;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ProgresoSincronizacionTest {
    private static final ParameterizedTypeReference<IntendenciaApiResponse<GeoPagosFacturaPendienteDto>> TIPO = new ParameterizedTypeReference<>() {};
    String pagina(int n, int cm, boolean mas, boolean conTotal) {
        return "{\"data\":[{\"CM\":"+cm+"}],\"meta\":{\"total\":"+(conTotal?"2":"null")+",\"last_page\":"+(conTotal?"2":"null")+",\"current_page\":"+n+",\"per_page\":1,\"from\":"+n+",\"to\":"+n+",\"has_more\":"+mas+"}}";
    }

    @Test void avanzaSoloConPaginasValidasYConservaContadoresDuranteReintentos() {
        var builder=RestClient.builder().baseUrl("https://test.invalid");
        var server=MockRestServiceServer.bindTo(builder).build();
        var paginador=new IntendenciaPaginador(builder.build(),1);
        server.expect(queryParam("page","1")).andRespond(withSuccess(pagina(1,1,true,false),MediaType.APPLICATION_JSON));
        server.expect(queryParam("page","2")).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        server.expect(queryParam("page","2")).andRespond(withSuccess(pagina(2,2,false,false),MediaType.APPLICATION_JSON));
        var eventos=new ArrayList<ProgresoSincronizacion>();
        try(var scope=SeguimientoSincronizacion.abrir(eventos::add)) {
            SeguimientoSincronizacion.etapa("DEUDAS",0);
            paginador.obtenerSinTotal("/pendientes",Map.of(),TIPO,GeoPagosFacturaPendienteDto::cm);
        }
        var reintento=eventos.stream().filter(p->p.intento()==2).findFirst().orElseThrow();
        assertThat(reintento.paginaActual()).isEqualTo(2);
        assertThat(reintento.paginasDescargadas()).isEqualTo(1);
        assertThat(reintento.registrosDescargados()).isEqualTo(1);
        assertThat(reintento.totalRegistros()).isNull();
        assertThat(eventos.getLast().registrosDescargados()).isEqualTo(2);
        assertThat(eventos.getLast().paginasDescargadas()).isEqualTo(2);
        server.verify();
    }

    @Test void publicaTotalesConocidosYReiniciaAlCambiarDeEtapa() {
        var builder=RestClient.builder().baseUrl("https://test.invalid");
        var server=MockRestServiceServer.bindTo(builder).build();
        var paginador=new IntendenciaPaginador(builder.build(),1);
        for(int n=1;n<=2;n++) server.expect(queryParam("page",String.valueOf(n))).andRespond(withSuccess(pagina(n,n,n<2,true),MediaType.APPLICATION_JSON));
        var eventos=new ArrayList<ProgresoSincronizacion>();
        try(var scope=SeguimientoSincronizacion.abrir(eventos::add)) {
            SeguimientoSincronizacion.etapa("COBROS",0);
            paginador.obtenerTodo("/cobros",Map.of(),TIPO,GeoPagosFacturaPendienteDto::cm);
            assertThat(eventos.getLast().totalRegistros()).isEqualTo(2);
            assertThat(eventos.getLast().totalPaginas()).isEqualTo(2);
            SeguimientoSincronizacion.etapa("CONTACTOS",0);
            assertThat(eventos.getLast().registrosDescargados()).isZero();
            assertThat(eventos.getLast().paginaActual()).isNull();
            assertThat(eventos.getLast().totalRegistros()).isNull();
        }
    }

    @Test void paginaInvalidaNoIncrementaElAvance() {
        var builder=RestClient.builder().baseUrl("https://test.invalid");
        var server=MockRestServiceServer.bindTo(builder).build();
        var paginador=new IntendenciaPaginador(builder.build(),1);
        server.expect(anything()).andRespond(withSuccess(pagina(1,1,true,false),MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(pagina(2,1,false,false),MediaType.APPLICATION_JSON));
        var eventos=new ArrayList<ProgresoSincronizacion>();
        try(var scope=SeguimientoSincronizacion.abrir(eventos::add)) {
            SeguimientoSincronizacion.etapa("DEUDAS",0);
            assertThatThrownBy(()->paginador.obtenerSinTotal("/pendientes",Map.of(),TIPO,GeoPagosFacturaPendienteDto::cm))
                .isInstanceOf(com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException.class);
        }
        assertThat(eventos.getLast().registrosDescargados()).isEqualTo(1);
        assertThat(eventos.getLast().paginasDescargadas()).isEqualTo(1);
    }

    @Test void liberaElSeguimientoYNoMezclaOtrosHilos() throws Exception {
        var eventos=new ArrayList<ProgresoSincronizacion>();
        var pool=Executors.newSingleThreadExecutor();
        try(var scope=SeguimientoSincronizacion.abrir(eventos::add)) {
            int inicial=eventos.size();
            pool.submit(()->SeguimientoSincronizacion.etapa("COBROS",0)).get(2,TimeUnit.SECONDS);
            assertThat(eventos).hasSize(inicial);
            SeguimientoSincronizacion.etapa("GUARDANDO",123);
            assertThat(eventos.getLast().registrosDescargados()).isEqualTo(123);
        } finally { pool.shutdownNow(); }
        int finalSize=eventos.size();
        SeguimientoSincronizacion.etapa("DEUDAS",0);
        assertThat(eventos).hasSize(finalSize);
        var nuevos=new ArrayList<ProgresoSincronizacion>();
        try(var scope=SeguimientoSincronizacion.abrir(nuevos::add)) {
            assertThat(nuevos.getLast().etapa()).isEqualTo("PREPARANDO");
            assertThat(nuevos.getLast().registrosDescargados()).isZero();
        }
    }
    @Test void cancelacionDuranteUnaRespuestaImpideLaSiguientePagina() {
        var builder=RestClient.builder().baseUrl("https://test.invalid");var server=MockRestServiceServer.bindTo(builder).build();
        var p=new IntendenciaPaginador(builder.build(),1);var cancelada=new java.util.concurrent.atomic.AtomicBoolean();
        server.expect(anything()).andRespond(request->{cancelada.set(true);return withSuccess(pagina(1,1,true,false),MediaType.APPLICATION_JSON).createResponse(request);});
        try(var scope=SeguimientoSincronizacion.abrir(x->{},cancelada::get)) {
            assertThatThrownBy(()->p.obtenerSinTotal("/pendientes",Map.of(),TIPO,GeoPagosFacturaPendienteDto::cm)).isInstanceOf(CancellationException.class);
        }
        server.verify();
    }
    @Test void cancelacionAntesDeArrancarNoDejaElHiloContaminado() {
        assertThatThrownBy(()->SeguimientoSincronizacion.abrir(p->{throw new CancellationException();},()->true)).isInstanceOf(CancellationException.class);
        SeguimientoSincronizacion.comprobarCancelacion();
    }

}
