package com.intendencia.gestion_morosidad_api.modules.usuario;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@NoArgsConstructor
@Getter
@Setter
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    // Las modificaciones se realizan con la actualización condicional de UsuarioRepository.
    @Column(nullable = false, updatable = false)
    private String password;

    @Column(nullable = false, updatable = false)
    private long versionCredencial;

    @Column(updatable = false)
    private Instant passwordTemporalHasta;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_roles", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false)
    private Set<Rol> roles = new HashSet<>();

    @Column(nullable = false)
    private Boolean activo = true;

    public Usuario(Long id, String username, String password, Set<Rol> roles, Boolean activo) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.roles = roles;
        this.activo = activo;
    }

    public boolean requiereCambioPassword() {
        return passwordTemporalHasta != null;
    }

    public boolean passwordTemporalVencida(Instant ahora) {
        return requiereCambioPassword() && !passwordTemporalHasta.isAfter(ahora);
    }
}
