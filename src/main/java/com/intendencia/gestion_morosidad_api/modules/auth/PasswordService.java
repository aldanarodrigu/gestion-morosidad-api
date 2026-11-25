package com.intendencia.gestion_morosidad_api.modules.auth;

import com.intendencia.gestion_morosidad_api.modules.usuario.Usuario;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordService {
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public void cambiar(String username, String actual, String nueva) {
        Usuario usuario = usuarioAutenticado(username);
        if (usuario.passwordTemporalVencida(Instant.now())
                || PoliticaPassword.superaLimiteBcrypt(actual)
                || !encoder.matches(actual, usuario.getPassword())) {
            throw new ReglaNegocioException("La contraseña actual no es válida.");
        }
        PoliticaPassword.validarNueva(nueva);
        if (nueva.equals(actual)) {
            throw new ReglaNegocioException("Elegí una contraseña diferente de la actual.");
        }
        guardar(usuario, nueva, null);
        log.info("Cambio de contraseña realizado por usuario id={}", usuario.getId());
    }

    public PasswordResetResponse restablecer(String username, long idUsuario) {
        Usuario administrador = usuarioAutenticado(username);
        if (administrador.getId().equals(idUsuario)) {
            throw new ReglaNegocioException("Para cambiar tu propia contraseña, usá la opción Cambiar contraseña.");
        }
        Usuario usuario = usuarios.findById(idUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        byte[] bytes = new byte[24];
        ALEATORIO.nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant venceEn = Instant.now().plus(PoliticaPassword.VIGENCIA_TEMPORAL);
        guardar(usuario, password, venceEn);
        log.info("Contraseña restablecida para usuario id={} por administrador id={}",
                usuario.getId(), administrador.getId());
        return new PasswordResetResponse(password, venceEn);
    }

    private Usuario usuarioAutenticado(String username) {
        return usuarios.findByUsername(username).filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    private void guardar(Usuario usuario, String password, Instant venceEn) {
        if (usuarios.actualizarCredencial(usuario.getId(), usuario.getVersionCredencial(),
                encoder.encode(password), venceEn) != 1) {
            throw new ReglaNegocioException("La contraseña se actualizó en otra operación. Volvé a intentarlo.");
        }
    }

    public record PasswordResetResponse(String passwordTemporal, Instant venceEn) {}
}
