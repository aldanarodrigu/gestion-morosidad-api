package com.intendencia.gestion_morosidad_api.modules.usuario;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    boolean existsByUsername(@NotBlank String username);
    /** El nombre de usuario es único sin distinguir mayúsculas, como el de acciones. */
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);
    Optional<Usuario> findByUsername(String username);

    @Query("SELECT COUNT(u) FROM Usuario u WHERE u.activo = true AND :rol MEMBER OF u.roles")
    long contarActivosConRol(Rol rol);
    //ya incluye save(), findAll(), findById(), deleteById() etc
}
