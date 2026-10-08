package com.intendencia.gestion_morosidad_api.modules.accion;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "acciones")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Accion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    public Boolean getActivo() {
        return this.activo;
    }
}
