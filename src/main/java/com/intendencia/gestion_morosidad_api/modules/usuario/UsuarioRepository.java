package com.intendencia.gestion_morosidad_api.modules.usuario;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    boolean existsByUsername(@NotBlank String username);
    /** El nombre de usuario es único sin distinguir mayúsculas, como el de acciones. */
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);
    Optional<Usuario> findByUsername(String username);
    List<Usuario> findAllByUsernameIgnoreCase(String username);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE usuarios SET password = :hash,
                version_credencial = version_credencial + 1,
                password_temporal_hasta = :temporalHasta
            WHERE id = :id AND version_credencial = :version
            """, nativeQuery = true)
    int actualizarCredencial(long id, long version, String hash, Instant temporalHasta);

    @Query("SELECT COUNT(u) FROM Usuario u WHERE u.activo = true AND :rol MEMBER OF u.roles")
    long contarActivosConRol(Rol rol);
    //ya incluye save(), findAll(), findById(), deleteById() etc
}
