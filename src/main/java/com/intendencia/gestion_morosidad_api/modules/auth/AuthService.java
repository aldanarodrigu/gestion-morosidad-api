package com.intendencia.gestion_morosidad_api.modules.auth;

import com.intendencia.gestion_morosidad_api.modules.auth.dto.LoginRequest;
import com.intendencia.gestion_morosidad_api.modules.auth.dto.LoginResponse;
import com.intendencia.gestion_morosidad_api.modules.usuario.Usuario;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;
    private final LimitadorLogin limite;

    public LoginResponse login(LoginRequest request, String ip) {
        return limite.autenticar(request.username(), ip, () -> validarCredenciales(request));
    }

    private LoginResponse validarCredenciales(LoginRequest request) {
        if (PoliticaPassword.superaLimiteBcrypt(request.password())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
        // Una autenticación que coincide con un cambio de clave solo puede emitir la versión anterior.
        // sin espacios y sin importar mayusculas
        String nombre = request.username().strip();
        var cuenta = usuarioRepository.findByUsername(nombre).or(() -> unicaSinMayusculas(nombre));
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                cuenta.map(Usuario::getUsername).orElse(nombre), request.password()));

        Usuario usuario = cuenta.orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
        if (usuario.passwordTemporalVencida(Instant.now())) {
            throw new CredentialsExpiredException("La contraseña temporal venció");
        }
        String token = tokenService.generateToken(usuario);
        return new LoginResponse(token, usuario.getId(), usuario.getUsername(),
                java.util.Set.copyOf(usuario.getRoles()), usuario.requiereCambioPassword());
    }

    // solo si hay una sola cuenta con ese nombre
    private Optional<Usuario> unicaSinMayusculas(String nombre) {
        List<Usuario> cuentas = usuarioRepository.findAllByUsernameIgnoreCase(nombre);
        return cuentas.size() == 1 ? Optional.of(cuentas.getFirst()) : Optional.empty();
    }
}
