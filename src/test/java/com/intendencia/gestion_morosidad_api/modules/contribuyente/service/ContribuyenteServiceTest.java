package com.intendencia.gestion_morosidad_api.modules.contribuyente.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.same;

import com.intendencia.gestion_morosidad_api.integration.intendencia.common.IntendenciaApiResponse;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContribuyenteServiceTest {

    @Mock private GeoPagosClient geoPagosClient;
    @Mock private ContribuyenteRepository contribuyenteRepository;
    @Mock private PadronRepository padronRepository;
    @InjectMocks private ContribuyenteService service;

    @Test
    void sincronizacionVinculaPadronYContribuyentePorCm() {
        GeoPagosContribuyenteDto fila = new GeoPagosContribuyenteDto(
                123, 4567, "COM", "San José", null, null, "Ana Pérez",
                null, null, null, null, null, null);
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(fila), null));
        when(padronRepository.findByCm("123")).thenReturn(Optional.empty());
        when(contribuyenteRepository.save(any(Contribuyente.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.sincronizarDesdeGeoPagos();

        ArgumentCaptor<Padron> padronCaptor = ArgumentCaptor.forClass(Padron.class);
        verify(padronRepository).save(padronCaptor.capture());
        Padron padron = padronCaptor.getValue();
        assertThat(padron.getCm()).isEqualTo("123");
        assertThat(padron.getNumeroPadron()).isEqualTo("4567");
        assertThat(padron.getContribuyente().getNombre()).isEqualTo("Ana Pérez");
        verify(contribuyenteRepository).save(padron.getContribuyente());
    }

    @Test
    void sincronizacionReutilizaElContribuyenteDelPadronExistente() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Nombre anterior");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setNumeroPadron("4567");
        padron.setContribuyente(contribuyente);
        GeoPagosContribuyenteDto fila = new GeoPagosContribuyenteDto(
                123, 4567, "COM", "San José", null, null, "Ana Pérez",
                null, null, null, null, null, null);
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(fila), null));
        when(padronRepository.findByCm("123")).thenReturn(Optional.of(padron));
        when(contribuyenteRepository.save(same(contribuyente))).thenReturn(contribuyente);

        service.sincronizarDesdeGeoPagos();

        verify(contribuyenteRepository).save(same(contribuyente));
        verify(padronRepository).save(same(padron));
        assertThat(padron.getContribuyente()).isSameAs(contribuyente);
        assertThat(contribuyente.getNombre()).isEqualTo("Ana Pérez");
    }

    @Test
    void busquedaPorCmUsaElCmDelPadronEnLaRespuesta() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Ana Pérez");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setContribuyente(contribuyente);
        when(padronRepository.findByCm("123")).thenReturn(Optional.of(padron));

        var respuesta = service.buscarPorCm("123").orElseThrow();

        assertThat(respuesta.cm()).isEqualTo("123");
        assertThat(respuesta.nombre()).isEqualTo("Ana Pérez");
    }

    @Test
    void listadoTomaElCmDeCadaPadron() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Ana Pérez");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setContribuyente(contribuyente);
        when(geoPagosClient.obtenerContribuyentes())
                .thenReturn(new IntendenciaApiResponse<>(List.of(), null));
        when(padronRepository.findAll()).thenReturn(List.of(padron));

        var respuesta = service.listarContribuyentes();

        assertThat(respuesta).hasSize(1);
        assertThat(respuesta.getFirst().cm()).isEqualTo("123");
        assertThat(respuesta.getFirst().nombre()).isEqualTo("Ana Pérez");
    }
}
