package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.*;
import com.intendencia.gestion_morosidad_api.modules.tributo.repository.TributoRepository;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SincronizacionRelojTest {
    @ParameterizedTest @EnumSource(OrigenDatos.class)
    void inicioFinYFechaGuardadaUsanUruguayInclusoSiElServidorEstaEnElDiaSiguiente(OrigenDatos origen) {
        TimeZone zonaAnterior = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try (var contexto = new AnnotationConfigApplicationContext()) {
            Clock reloj = Clock.fixed(Instant.parse("2026-10-21T01:30:00Z"), ZoneId.of("America/Montevideo"));
            var deudas = mock(DeudaRepository.class);
            var segmentos = mock(ConfiguracionSegmentoService.class);
            when(segmentos.reglasVigentes()).thenReturn(new ReglasSegmentacion(List.of()));
            contexto.registerBean(Clock.class, () -> reloj);
            contexto.registerBean(DeudaRepository.class, () -> deudas);
            contexto.registerBean(PadronRepository.class, () -> mock(PadronRepository.class));
            contexto.registerBean(ContribuyenteRepository.class, () -> mock(ContribuyenteRepository.class));
            contexto.registerBean(SincronizacionContactosService.class, () -> mock(SincronizacionContactosService.class));
            contexto.registerBean(TributoRepository.class, () -> mock(TributoRepository.class));
            contexto.registerBean(ConfiguracionSegmentoService.class, () -> segmentos);
            contexto.registerBean(SincronizacionDeudasProcessor.class);
            contexto.refresh();
            LocalDateTime inicio = LocalDateTime.now(reloj);
            var fila = new GeoPagosFacturaPendienteDto(123, "4567", null, null, "CIU", null,
                    "Prueba sintetica", "DOC-TEST", null, null, null, null, "NO", null,
                    new BigDecimal("100"), inicio.toLocalDate().minusDays(10), inicio.toLocalDate().minusDays(1), "2026");

            var resultado = contexto.getBean(SincronizacionDeudasProcessor.class)
                    .procesar(List.of(fila), List.of(), List.of(), inicio.toLocalDate(), inicio, origen);

            @SuppressWarnings({"unchecked", "rawtypes"})
            ArgumentCaptor<Iterable<Deuda>> captor = ArgumentCaptor.forClass((Class) Iterable.class);
            verify(deudas).saveAll(captor.capture());
            assertThat(resultado.inicio()).isEqualTo(LocalDateTime.of(2026,10,20,22,30));
            assertThat(resultado.fin()).isEqualTo(inicio);
            assertThat(captor.getValue()).singleElement().satisfies(deuda ->
                    assertThat(deuda.getFechaSincronizacion()).isEqualTo(inicio));
        } finally {
            TimeZone.setDefault(zonaAnterior);
        }
    }
}
