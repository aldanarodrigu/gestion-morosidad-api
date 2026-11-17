package com.intendencia.gestion_morosidad_api.modules.usuario;

import com.intendencia.gestion_morosidad_api.modules.usuario.dto.UsuarioRequest;
import com.intendencia.gestion_morosidad_api.modules.usuario.dto.UsuarioResponse;
import com.intendencia.gestion_morosidad_api.modules.usuario.dto.UsuarioUpdateRequest;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoYaExisteException;
import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {

        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuarioResponse> getAllUsers() {
        return usuarioRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public UsuarioResponse getUsuarioById(Long id) {

        Usuario usuario = usuarioRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario con id " + id + " no encontrado"));

        return mapToResponse(usuario);
    }

    @Transactional
    public UsuarioResponse saveUser(UsuarioRequest usuarioRequest) {

        if (usuarioRepository.existsByUsernameIgnoreCase(usuarioRequest.username().trim())) {
            throw new RecursoYaExisteException("El usuario '" + usuarioRequest.username().trim() + "' ya existe");
        }

        Usuario usuario = new Usuario();

        usuario.setUsername(usuarioRequest.username().trim());
        usuario.setPassword(
                passwordEncoder.encode(usuarioRequest.password())
        );
        usuario.setRoles(new HashSet<>(usuarioRequest.roles()));
        usuario.setActivo(true);

        usuarioRepository.save(usuario);

        return mapToResponse(usuario);
    }

    @Transactional
    public UsuarioResponse updateUser(
            Long id,
            UsuarioUpdateRequest request,
            String usuarioActual) {

        Usuario usuario = usuarioRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario con id " + id + " no encontrado"));

        if (usuarioRepository.existsByUsernameIgnoreCaseAndIdNot(request.username().trim(), id)) {
            throw new RecursoYaExisteException("El usuario '" + request.username().trim() + "' ya existe");
        }

        // El sistema nunca queda sin administrador: nadie se quita el rol a sí mismo
        // ni se baja al último administrador activo.
        boolean eraAdministradorActivo = usuario.getActivo() && usuario.getRoles().contains(Rol.ADMINISTRADOR);
        boolean quedaAdministradorActivo = request.activo() && request.roles().contains(Rol.ADMINISTRADOR);
        if (eraAdministradorActivo && !quedaAdministradorActivo) {
            if (usuario.getUsername().equals(usuarioActual)) {
                throw new ReglaNegocioException("No podés quitarte el rol de administrador ni desactivar tu propia cuenta.");
            }
            if (usuarioRepository.contarActivosConRol(Rol.ADMINISTRADOR) <= 1) {
                throw new ReglaNegocioException("Debe quedar al menos un administrador activo.");
            }
        }

        usuario.setUsername(request.username().trim());
        usuario.setRoles(new HashSet<>(request.roles()));
        usuario.setActivo(request.activo());

        usuarioRepository.save(usuario);

        return mapToResponse(usuario);
    }

    private UsuarioResponse mapToResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getUsername(),
                java.util.Set.copyOf(usuario.getRoles()),
                usuario.getActivo()
        );
    }
}