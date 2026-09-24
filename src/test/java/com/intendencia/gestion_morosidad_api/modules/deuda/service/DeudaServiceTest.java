package com.intendencia.gestion_morosidad_api.modules.deuda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.intendencia.gestion_morosidad_api.modules.deuda.dto.DeudaResponse;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ConfiguracionSegmentoService;
import com.intendencia.gestion_morosidad_api.modules.tributo.dto.TributoResponse;
import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeudaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Montevideo");

    @Mock
    private DeudaRepository deudaRepository;

    @Mock
    private ConfiguracionSegmentoService configuracionSegmentoService;

    private DeudaService deudaService;

    @BeforeEach
    void setUp() {
        // "Hoy" fijo: 2026-09-23
        Clock clock = Clock.fixed(Instant.parse("2026-09-23T15:00:00Z"), ZONA);
        deudaService = new DeudaService(deudaRepository, configuracionSegmentoService, clock);
    }

    @Test
    void obtenerCalculaDiasDeAtrasoDesdeElVencimientoMasAntiguo() {
        Deuda deuda = deuda(1L, "100", LocalDate.of(2026, 6, 25));
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));

        DeudaResponse respuesta = deudaService.obtener(1L);

        assertThat(respuesta.diasAtraso()).isEqualTo(90);
        assertThat(respuesta.importe()).isEqualByComparingTo("1550.00");
        assertThat(respuesta.padron().numeroPadron()).isEqualTo("100");
        assertThat(respuesta.padron().contribuyente().nombre()).isEqualTo("Persona de ejemplo");
        assertThat(respuesta.contribuyente().nombre()).isEqualTo("Persona de ejemplo");
    }

    @Test
    void obtenerSinFechaDeVencimientoDevuelveDiasAtrasoNulo() {
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda(1L, "100", null)));

        assertThat(deudaService.obtener(1L).diasAtraso()).isNull();
    }

    @Test
    void obtenerInexistenteLanzaRecursoNoEncontrado() {
        when(deudaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deudaService.obtener(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void listarTributosLosOrdenaComoNumeros() {
        Deuda deuda = deuda(1L, "100", LocalDate.of(2026, 1, 1));
        deuda.getTributos().addAll(List.of(tributo("3802"), tributo("801"), tributo("101")));
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));

        assertThat(deudaService.listarTributos(1L))
                .extracting(TributoResponse::codigo)
                .containsExactly("101", "801", "3802");
    }

    @Test
    void listarPorPadronIgnoraEspaciosDelParametro() {
        when(deudaRepository.findByPadronNumeroPadronOrderByDeudaDesdeAsc("4567"))
                .thenReturn(List.of(deuda(1L, "4567", LocalDate.of(2025, 1, 1))));

        assertThat(deudaService.listarPorPadron(" 4567 ")).hasSize(1);
    }

    @Test
    void actualizarEstadoGuardaElNuevoEstado() {
        Deuda deuda = deuda(1L, "100", LocalDate.of(2026, 1, 1));
        when(deudaRepository.findById(1L)).thenReturn(Optional.of(deuda));
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(inv -> inv.getArgument(0));

        DeudaResponse respuesta = deudaService.actualizarEstado(1L, EstadoDeuda.EN_GESTION);

        assertThat(respuesta.estado()).isEqualTo(EstadoDeuda.EN_GESTION);
        assertThat(deuda.getEstado()).isEqualTo(EstadoDeuda.EN_GESTION);
    }

    @Test
    void actualizarEstadoDeDeudaInexistenteLanzaRecursoNoEncontrado() {
        when(deudaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deudaService.actualizarEstado(99L, EstadoDeuda.CANCELADA))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private static Tributo tributo(String codigo) {
        Tributo tributo = new Tributo();
        tributo.setCodigo(codigo);
        return tributo;
    }

    private static Deuda deuda(Long id, String numeroPadron, LocalDate deudaDesde) {
        Contribuyente contribuyente = new Contribuyente();
        contribuyente.setNombre("Persona de ejemplo");

        Padron padron = new Padron();
        padron.setCm("CM-" + id);
        padron.setNumeroPadron(numeroPadron);
        padron.setContribuyente(contribuyente);

        Deuda deuda = new Deuda();
        deuda.setId(id);
        deuda.setPadron(padron);
        deuda.setImporte(new BigDecimal("1550.00"));
        deuda.setDeudaDesde(deudaDesde);
        return deuda;
    }
}
