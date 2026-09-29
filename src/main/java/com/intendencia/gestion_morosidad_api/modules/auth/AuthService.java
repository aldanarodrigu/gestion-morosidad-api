package com.intendencia.gestion_morosidad_api.modules.auth;

import com.intendencia.gestion_morosidad_api.modules.auth.dto.LoginRequest;
import com.intendencia.gestion_morosidad_api.modules.auth.dto.LoginResponse;
import com.intendencia.gestion_morosidad_api.modules.usuario.Usuario;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        Usuario usuario = usuarioRepository.findByUsername(request.username())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        String token = tokenService.generateToken(usuario);
        return new LoginResponse(token, usuario.getId(), usuario.getUsername(), usuario.getRol());
    }
}
