package com.intendencia.gestion_morosidad_api.modules.gestion;

import com.intendencia.gestion_morosidad_api.modules.accion.*;
import com.intendencia.gestion_morosidad_api.modules.accion_segmento.*;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.*;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.gestion.dto.*;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.usuario.*;
import com.intendencia.gestion_morosidad_api.shared.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestionServiceTest {
    @Mock GestionRepository gestiones;
    @Mock DeudaRepository deudas;
    @Mock AccionRepository acciones;
    @Mock AccionSegmentoRepository segmentos;
    @Mock UsuarioRepository usuarios;
    GestionService service;
    Deuda deuda;
    Accion accion;
    Usuario usuario;
    final Clock clock = Clock.fixed(Instant.parse("2026-10-12T20:00:00Z"), ZoneId.of("America/Montevideo"));

    @BeforeEach void preparar() {
        service = new GestionService(gestiones, deudas, acciones, segmentos, usuarios, clock);
        Padron padron = new Padron(); padron.setCm("123"); padron.setNumeroPadron("456");
        deuda = new Deuda(); deuda.setId(10L); deuda.setPadron(padron);
        accion = new Accion(); accion.setId(2L); accion.setNombre("Contacto telefónico");
        usuario = new Usuario(); usuario.setId(3L); usuario.setUsername("notificador");
    }
    GestionRequest request() {
        return new GestionRequest(LocalDateTime.of(2000, 1, 1, 0, 0), ResultadoGestion.EXITOSA, "Se contactó", 10L, 2L);
    }
    void referencias() {
        when(deudas.findById(10L)).thenReturn(Optional.of(deuda));
        when(acciones.findById(2L)).thenReturn(Optional.of(accion));
    }
    Gestion gestion(Long id) {
        Gestion g = new Gestion(); g.setId(id); g.setDeuda(deuda); g.setAccion(accion);
        g.setFecha(LocalDateTime.now(clock)); g.setResultado(ResultadoGestion.EXITOSA); return g;
    }
    @Test void guardaDeudaAccionUsuarioYFechaDelServidorSinCambiarEstado() {
        referencias(); when(usuarios.findByUsername("notificador")).thenReturn(Optional.of(usuario));
        when(gestiones.save(any())).thenAnswer(i -> { Gestion g = i.getArgument(0); g.setId(9L); return g; });
        var r = service.createGestion(request(), "notificador");
        assertThat(r.usuarioId()).isEqualTo(3L); assertThat(r.usuarioNombre()).isEqualTo("notificador");
        assertThat(r.fecha()).isEqualTo(LocalDateTime.now(clock)); assertThat(r.deudaId()).isEqualTo(10L);
        assertThat(r.cm()).isEqualTo("123"); assertThat(r.accionNombre()).isEqualTo("Contacto telefónico");
        assertThat(deuda.getEstado()).isEqualTo(EstadoDeuda.PENDIENTE);
        verify(deudas, never()).save(any());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(ResultadoGestion.class)
    void guardaTodosLosResultadosAdmitidos(ResultadoGestion resultado) {
        referencias(); when(usuarios.findByUsername("notificador")).thenReturn(Optional.of(usuario));
        when(gestiones.save(any())).thenAnswer(i -> i.getArgument(0));
        var r = service.createGestion(new GestionRequest(null, resultado, null, 10L, 2L), "notificador");
        assertThat(r.resultado()).isEqualTo(resultado);
    }
    @Test void historialDeUnaDeudaAdmiteMultiplesGestionesYAutorAnteriorDesconocido() {
        when(deudas.existsById(10L)).thenReturn(true);
        when(gestiones.findByDeuda_IdOrderByFechaDescIdDesc(10L)).thenReturn(List.of(gestion(2L), gestion(1L)));
        var r = service.listarPorDeuda(10L);
        assertThat(r).extracting(GestionResponse::id).containsExactly(2L, 1L);
        assertThat(r).allSatisfy(g -> { assertThat(g.deudaId()).isEqualTo(10L); assertThat(g.usuarioNombre()).isNull(); });
        verify(gestiones, never()).findAll();
    }
    @Test void deudaExistenteSinGestionesDevuelveListaVacia() {
        when(deudas.existsById(10L)).thenReturn(true);
        assertThat(service.listarPorDeuda(10L)).isEmpty();
    }
    @Test void historialDeDeudaInexistenteDevuelveError() {
        assertThatThrownBy(() -> service.listarPorDeuda(99L)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(gestiones);
    }
    @Test void noGuardaConDeudaInexistente() {
        assertThatThrownBy(() -> service.createGestion(request(), "notificador")).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(gestiones);
    }
    @Test void noGuardaConAccionInexistente() {
        when(deudas.findById(10L)).thenReturn(Optional.of(deuda));
        assertThatThrownBy(() -> service.createGestion(request(), "notificador")).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(gestiones);
    }
    @Test void noGuardaAccionesDeshabilitadas() {
        referencias(); accion.setActivo(false);
        assertThatThrownBy(() -> service.createGestion(request(), "notificador")).isInstanceOf(ReglaNegocioException.class);
        verifyNoInteractions(gestiones);
    }
    @Test void noGuardaConUsuarioInexistenteOInactivo() {
        referencias(); usuario.setActivo(false);
        when(usuarios.findByUsername("notificador")).thenReturn(Optional.of(usuario));
        assertThatThrownBy(() -> service.createGestion(request(), "notificador")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(gestiones);
    }
    @Test void deudaSinSegmentoNoTieneAccionesDisponibles() {
        when(deudas.findById(10L)).thenReturn(Optional.of(deuda));
        assertThat(service.getAccionesDisponibles(10L)).isEmpty(); verifyNoInteractions(segmentos);
    }
}
