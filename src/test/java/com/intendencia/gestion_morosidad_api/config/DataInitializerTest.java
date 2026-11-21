package com.intendencia.gestion_morosidad_api.config;

import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import com.intendencia.gestion_morosidad_api.modules.usuario.Usuario;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataInitializerTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = spy(new BCryptPasswordEncoder());

    private void arrancar() throws Exception {
        new DataInitializer().initAdmin(usuarios, encoder, new MockEnvironment(), "").run();
    }

    @Test
    void conLaBaseVaciaCreaAdminActivoConRolAdministradorYContrasenaHasheada() throws Exception {
        when(usuarios.count()).thenReturn(0L);

        arrancar();

        var guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(guardado.capture());
        Usuario admin = guardado.getValue();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getRoles()).containsExactly(Rol.ADMINISTRADOR);
        assertThat(admin.getActivo()).isTrue();
        assertThat(admin.getPassword()).isNotEqualTo("admin123");
        assertThat(encoder.matches("admin123", admin.getPassword())).isTrue();
    }

    @Test
    void siAdminYaExisteNoLoVuelveACrearNiLePisaLaContrasena() throws Exception {
        when(usuarios.count()).thenReturn(1L);
        when(usuarios.existsByUsername("admin")).thenReturn(true);

        arrancar();

        verify(usuarios, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void siRenombraronAlAdminNoSeRecreaConLaContrasenaPorDefecto() throws Exception {
        // Hay usuarios, pero ninguno se llama "admin" (por ejemplo, lo renombraron a "intendencia").
        when(usuarios.count()).thenReturn(1L);
        when(usuarios.existsByUsername("admin")).thenReturn(false);

        arrancar();

        verify(usuarios, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void reiniciosSucesivosCreanAdminUnaSolaVez() throws Exception {
        // Primer arranque: base vacía; a partir del segundo, ya hay un usuario guardado.
        when(usuarios.count()).thenReturn(0L, 1L, 1L);

        arrancar();
        arrancar();
        arrancar();

        verify(usuarios, times(1)).save(any());
    }
    @Test
    void prodRequiereClaveConfiguradaCuandoLaBaseEstaVacia() {
        var prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        var initializer = new DataInitializer();
        assertThatThrownBy(() -> initializer.initAdmin(usuarios, encoder, prod, "").run())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");
        assertThatThrownBy(() -> initializer.initAdmin(usuarios, encoder, prod, "admin123").run())
                .isInstanceOf(com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException.class);
        verify(usuarios, never()).save(any());
    }

    @Test
    void prodUtilizaSoloLaClaveConfiguradaAlCrearElPrimerAdministrador() throws Exception {
        var prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        var configurada = "clave-inicial-probada-2026";
        new DataInitializer().initAdmin(usuarios, encoder, prod, configurada).run();

        var admin = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(admin.capture());
        assertThat(encoder.matches(configurada, admin.getValue().getPassword())).isTrue();
        assertThat(encoder.matches("admin123", admin.getValue().getPassword())).isFalse();
    }
}
