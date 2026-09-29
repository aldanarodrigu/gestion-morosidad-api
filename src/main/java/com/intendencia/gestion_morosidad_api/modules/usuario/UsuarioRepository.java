package com.intendencia.gestion_morosidad_api.modules.usuario;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    boolean existsByUsername(@NotBlank String username);
    Optional<Usuario> findByUsername(String username);
    //ya incluye save(), findAll(), findById(), deleteById() etc
}
