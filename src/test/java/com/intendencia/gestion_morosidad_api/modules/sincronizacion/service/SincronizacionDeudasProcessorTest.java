package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ConfiguracionSegmentoService;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ReglasSegmentacion;
import com.intendencia.gestion_morosidad_api.modules.tributo.repository.TributoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SincronizacionDeudasProcessorTest {

    @Mock private DeudaRepository deudaRepository;
    @Mock private PadronRepository padronRepository;
    @Mock private ContribuyenteRepository contribuyenteRepository;
    @Mock private TributoRepository tributoRepository;
    @Mock private ConfiguracionSegmentoService configuracionSegmentoService;
    @InjectMocks private SincronizacionDeudasProcessor processor;

    @Test
    void actualizaElContribuyenteDelPadronExistenteSinDuplicarlo() {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Nombre anterior");
        Padron padron = new Padron();
        padron.setCm("123");
        padron.setNumeroPadron("4567");
        padron.setContribuyente(contribuyente);
        when(padronRepository.findAll()).thenReturn(List.of(padron));
        when(configuracionSegmentoService.reglasVigentes()).thenReturn(new ReglasSegmentacion(List.of()));
        GeoPagosFacturaPendienteDto fila = new GeoPagosFacturaPendienteDto(
                123, 4567, null, null, "COM", null, "Ana Pérez", "1234567-8",
                null, null, null, null, "NO", null, new BigDecimal("100.00"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), "2026");

        var resultado = processor.procesar(List.of(fila), List.of(),
                LocalDate.of(2026, 9, 24), LocalDateTime.of(2026, 9, 24, 12, 0));

        assertThat(resultado.deudasCreadas()).isEqualTo(1);
        assertThat(padron.getContribuyente()).isSameAs(contribuyente);
        assertThat(contribuyente.getNombre()).isEqualTo("Ana Pérez");
        assertThat(contribuyente.getDocumento()).isEqualTo("1234567-8");
        verify(contribuyenteRepository).saveAll(argThat(nuevos -> !nuevos.iterator().hasNext()));
    }
}
