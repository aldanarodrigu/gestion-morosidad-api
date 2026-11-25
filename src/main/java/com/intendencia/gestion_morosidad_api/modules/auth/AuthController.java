package com.intendencia.gestion_morosidad_api.modules.auth;

import com.intendencia.gestion_morosidad_api.modules.auth.dto.LoginRequest;
import com.intendencia.gestion_morosidad_api.modules.auth.dto.LoginResponse;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UsuarioRepository usuarios;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public SesionResponse sesion(Principal principal) {
        var usuario = usuarios.findByUsername(principal.getName())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        return new SesionResponse(usuario.getId(), usuario.getUsername(),
                Set.copyOf(usuario.getRoles()), usuario.getActivo(), usuario.requiereCambioPassword());
    }

    public record SesionResponse(Long id, String username, Set<Rol> roles, Boolean activo,
            boolean requiereCambioPassword) {}

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        // Detrás del proxy, Tomcat toma la IP del cliente de X-Real-IP (ver server.tomcat.remoteip).
        return ResponseEntity.ok(authService.login(loginRequest, request.getRemoteAddr()));
    }
}
