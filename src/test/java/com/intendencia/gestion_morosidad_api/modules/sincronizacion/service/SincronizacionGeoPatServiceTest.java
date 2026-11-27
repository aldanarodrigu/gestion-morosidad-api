package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopat.client.GeoPatClient;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import com.intendencia.gestion_morosidad_api.shared.exception.OperacionEnCursoException;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpServerErrorException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class SincronizacionGeoPatServiceTest {
    @Mock GeoPatClient geoPatClient;
    @Mock GeoPagosClient geoPagosClient;
    @Mock DeudaRepository deudaRepository;
    @Mock SincronizacionDeudasProcessor processor;
    @Mock PeriodoSincronizacionService periodo;
    SincronizacionDeudasService service;

    @BeforeEach void preparar() {
        service = new SincronizacionDeudasService(geoPagosClient, geoPatClient, processor, deudaRepository,
                Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneId.of("America/Montevideo")), periodo);
        lenient().when(periodo.anioMinimo()).thenReturn(2013);
        ReflectionTestUtils.setField(service, "usuarioApi", "test");
        ReflectionTestUtils.setField(service, "diasCobrosIniciales", 90);
    }

    @Test void usaClienteYFechaAnteriorExclusivosDeSUCIVE() {
        when(geoPatClient.obtenerFacturasPendientes(LocalDate.of(2013,1,1),LocalDate.of(2026,10,16))).thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(deudaRepository.ultimaSincronizacionPorOrigen(OrigenDatos.SUCIVE))
                .thenReturn(Optional.of(LocalDateTime.of(2026,10,10,12,0)));
        when(geoPatClient.obtenerFacturasCanceladas(LocalDate.of(2026,10,9), LocalDate.of(2026,10,17)))
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(geoPatClient.obtenerContribuyentes()).thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        service.sincronizar(OrigenDatos.SUCIVE);
        verify(processor).procesar(eq(List.of()), eq(List.of()), eq(List.of()), eq(LocalDate.of(2026,10,16)), any(), eq(OrigenDatos.SUCIVE), eq(2013));
        verifyNoInteractions(geoPagosClient);
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void publicaEtapasYFinalizaSoloDespuesDelProcesador(boolean falla) {
        when(geoPatClient.obtenerFacturasPendientes(any(),any())).thenReturn(new IntendenciaApiResponse<>(List.of(),null));
        when(deudaRepository.ultimaSincronizacionPorOrigen(OrigenDatos.SUCIVE)).thenReturn(Optional.empty());
        when(geoPatClient.obtenerFacturasCanceladas(any(),any())).thenReturn(new IntendenciaApiResponse<>(List.of(),null));
        when(geoPatClient.obtenerContribuyentes()).thenReturn(new IntendenciaApiResponse<>(List.of(),null));
        var eventos=new java.util.ArrayList<com.intendencia.gestion_morosidad_api.integration.intendencia.common.ProgresoSincronizacion>();
        when(processor.procesar(any(),any(),any(),any(),any(),eq(OrigenDatos.SUCIVE),eq(2013))).thenAnswer(i->{
            assertThat(eventos.getLast().etapa()).isEqualTo("GUARDANDO");
            if(falla) throw new IntegracionExternaException("Fallo al confirmar la transacción");
            return null;
        });
        try(var scope=com.intendencia.gestion_morosidad_api.integration.intendencia.common.SeguimientoSincronizacion.abrir(eventos::add)) {
            if(falla) assertThatThrownBy(()->service.sincronizar(OrigenDatos.SUCIVE)).isInstanceOf(IntegracionExternaException.class);
            else service.sincronizar(OrigenDatos.SUCIVE);
        }
        if(falla) assertThat(eventos).extracting(p->p.etapa()).containsExactly("PREPARANDO","DEUDAS","COBROS","CONTACTOS","GUARDANDO");
        else assertThat(eventos).extracting(p->p.etapa()).containsExactly("PREPARANDO","DEUDAS","COBROS","CONTACTOS","GUARDANDO","FINALIZADA");
    }

    @ParameterizedTest @EnumSource(OrigenDatos.class)
    void sinLimiteNoInventaUnaFechaInicial(OrigenDatos origen) {
        when(periodo.anioMinimo()).thenReturn(null);
        var client = origen == OrigenDatos.SUCIVE ? geoPatClient : geoPagosClient;
        when(client.obtenerFacturasPendientes(null,LocalDate.of(2026,10,16))).thenReturn(new IntendenciaApiResponse<>(List.of(),null));
        when(deudaRepository.ultimaSincronizacionPorOrigen(origen)).thenReturn(Optional.empty());
        when(client.obtenerFacturasCanceladas(any(),any())).thenReturn(new IntendenciaApiResponse<>(List.of(),null));
        when(client.obtenerContribuyentes()).thenReturn(new IntendenciaApiResponse<>(List.of(),null));
        service.sincronizar(origen);
        verify(processor).procesar(any(),any(),any(),any(),any(),eq(origen),isNull());
    }

    @Test void respuestaInvalidaNoEscribeYLiberaElBloqueo() {
        when(geoPatClient.obtenerFacturasPendientes(LocalDate.of(2013,1,1),LocalDate.of(2026,10,16))).thenReturn(null);
        for (int i=0; i<2; i++)
            assertThatThrownBy(() -> service.sincronizar(OrigenDatos.SUCIVE)).isInstanceOf(IntegracionExternaException.class).hasMessageContaining("GeoPat");
        verifyNoInteractions(processor, deudaRepository, geoPagosClient);
    }

    @Test void falloDeContribuyentesNoImpideGuardarDeudasValidas() {
        when(geoPatClient.obtenerFacturasPendientes(LocalDate.of(2013,1,1),LocalDate.of(2026,10,16))).thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(deudaRepository.ultimaSincronizacionPorOrigen(OrigenDatos.SUCIVE)).thenReturn(Optional.empty());
        when(geoPatClient.obtenerFacturasCanceladas(LocalDate.of(2026,7,18), LocalDate.of(2026,10,17)))
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(geoPatClient.obtenerContribuyentes()).thenReturn(null);
        service.sincronizar(OrigenDatos.SUCIVE);
        verify(processor).procesar(eq(List.of()), eq(List.of()), isNull(), any(), any(), eq(OrigenDatos.SUCIVE), eq(2013));
    }

    @Test
    void distingueUnFormatoInvalidoDeUnFalloDeConexion() {
        when(geoPatClient.obtenerFacturasPendientes(any(), any())).thenThrow(
                new RestClientException("Error al leer la respuesta", new HttpMessageConversionException("Formato inválido")));

        assertThatThrownBy(() -> service.sincronizar(OrigenDatos.SUCIVE))
                .isInstanceOf(IntegracionExternaException.class)
                .hasMessageContaining("GeoPat devolvió datos inválidos al consultar facturas pendientes");
        verifyNoInteractions(processor);
    }

    @Test void evitaSincronizacionesSimultaneasEntreOrigenes() {
        ((AtomicBoolean) ReflectionTestUtils.getField(service, "enCurso")).set(true);
        assertThatThrownBy(() -> service.sincronizar(OrigenDatos.SUCIVE)).isInstanceOf(OperacionEnCursoException.class);
        verifyNoInteractions(geoPatClient, geoPagosClient, processor);
    }

    @ParameterizedTest @EnumSource(OrigenDatos.class)
    void falloEnCobrosObligatoriosNoProcesaPendientesYLiberaElBloqueo(OrigenDatos origen) {
        var client = origen == OrigenDatos.SUCIVE ? geoPatClient : geoPagosClient;
        when(client.obtenerFacturasPendientes(LocalDate.of(2013,1,1),LocalDate.of(2026,10,16))).thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(deudaRepository.ultimaSincronizacionPorOrigen(origen)).thenReturn(Optional.empty());
        when(client.obtenerFacturasCanceladas(any(), any())).thenThrow(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT));

        for (int intento=0; intento<2; intento++)
            assertThatThrownBy(() -> service.sincronizar(origen)).isInstanceOf(IntegracionExternaException.class)
                    .hasMessageContaining("504").hasMessageContaining(origen.proveedor());

        verifyNoInteractions(processor);
        if (origen == OrigenDatos.SUCIVE) verifyNoInteractions(geoPagosClient);
        else verifyNoInteractions(geoPatClient);
    }
}
