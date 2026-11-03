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
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FacturasPadronServiceTest {
    DeudaRepository repo = mock(DeudaRepository.class);
    MockRestServiceServer server;
    FacturasPadronService service;
    @BeforeEach void preparar() {
        var builder = RestClient.builder().baseUrl("https://api.example.test");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new FacturasPadronService(repo, builder.build());
    }
    void deuda(OrigenDatos origen) {
        var padron = new Padron(); padron.setCm("1001"); padron.setOrigen(origen);
        var d = new Deuda(); d.setPadron(padron);
        when(repo.findById(42L)).thenReturn(Optional.of(d));
    }
    @ParameterizedTest @EnumSource(OrigenDatos.class)
    void eligeProveedorDelPadronYConservaImportesUnidadesYConvenios(OrigenDatos origen) {
        deuda(origen);
        String ruta = origen == OrigenDatos.SUCIVE ? "/api/v1/utec/sucive/facturas" : "/api/v1/utec/facturas";
        server.expect(requestTo("https://api.example.test" + ruta + "?cm=1001&incluir_convenios=0&estado=PENDIENTE&vencida=1"))
                .andRespond(withSuccess("""
                {"data":[{"factura_id":"000123","cm":1001,"monto":"1.23456","moneda":null,"unidad_codigo":7,
                "convenio_cm":4000,"tributo":"Tasa","vencida":null,"fecha_vencimiento":null,
                "montos_por_unidad":[{"monto":"12.00001","moneda":"UI","unidad_codigo":3}]}],"meta":{"total":1}}
                """,MediaType.APPLICATION_JSON));
        var f = service.consultar(42L,"PENDIENTE",true,false).data().getFirst();
        assertEquals("000123", f.facturaId()); assertEquals("1.23456",f.monto());
        assertNull(f.moneda()); assertEquals(7L,f.unidadCodigo()); assertEquals(4000L,f.convenioCm());
        assertEquals("12.00001",f.montosPorUnidad().getFirst().monto());
        server.verify(); verify(repo,never()).save(any());
    }
    @Test void sinFiltrosNoEnviaPaginacionYConservaListaVacia() {
        deuda(OrigenDatos.INTENDENCIA);
        server.expect(requestTo("https://api.example.test/api/v1/utec/facturas?cm=1001&incluir_convenios=1"))
                .andRespond(withSuccess("{\"data\":[],\"meta\":{\"total\":0}}",MediaType.APPLICATION_JSON));
        assertTrue(service.consultar(42L,null,null,true).data().isEmpty()); server.verify();
    }
    @Test void deudaInexistenteNoLlamaApi() {
        assertThrows(RecursoNoEncontradoException.class, () -> service.consultar(9L,null,null,true)); server.verify();
    }
    @Test void errorExternoSeInformaSinExponerRespuesta() {
        deuda(OrigenDatos.INTENDENCIA);
        server.expect(anything()).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT).body("detalle interno"));
        var error = assertThrows(IntegracionExternaException.class, () -> service.consultar(42L,null,null,true));
        assertTrue(error.getMessage().contains("504")); assertFalse(error.getMessage().contains("interno"));
    }
    @Test void rechazaRespuestaParcial() {
        deuda(OrigenDatos.INTENDENCIA);
        server.expect(anything()).andRespond(withSuccess("{\"data\":[],\"meta\":{\"total\":1}}",MediaType.APPLICATION_JSON));
        assertThrows(IntegracionExternaException.class, () -> service.consultar(42L,null,null,true));
    }
    @Test void rechazaEstadoInvalido() {
        assertThrows(ReglaNegocioException.class, () -> service.consultar(42L,"INVALIDO",null,true)); verifyNoInteractions(repo);
    }
}
