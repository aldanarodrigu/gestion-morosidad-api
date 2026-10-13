package com.intendencia.gestion_morosidad_api.modules.usuario;

import com.intendencia.gestion_morosidad_api.modules.usuario.dto.UsuarioRequest;
import com.intendencia.gestion_morosidad_api.modules.usuario.dto.UsuarioResponse;
import com.intendencia.gestion_morosidad_api.modules.usuario.dto.UsuarioUpdateRequest;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoNoEncontradoException;
import com.intendencia.gestion_morosidad_api.shared.exception.RecursoYaExisteException;
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

        if (usuarioRepository.existsByUsername(usuarioRequest.username().trim())) {
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
            UsuarioUpdateRequest request) {

        Usuario usuario = usuarioRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario con id " + id + " no encontrado"));

        if (!usuario.getUsername().equals(request.username().trim()) && usuarioRepository.existsByUsername(request.username().trim())) {
            throw new RecursoYaExisteException("El usuario '" + request.username().trim() + "' ya existe");
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