package com.intendencia.gestion_morosidad_api.config;

import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import com.intendencia.gestion_morosidad_api.modules.usuario.Usuario;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
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
        new DataInitializer().initAdmin(usuarios, encoder).run();
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
}
