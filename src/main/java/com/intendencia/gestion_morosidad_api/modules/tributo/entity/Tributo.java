package com.intendencia.gestion_morosidad_api.modules.tributo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tributo municipal identificado por su código (ej. 101, 3802).
 * El código es texto: es un identificador, no un número operable.
 */
@Entity
@Table(name = "tributos")
@Getter
@Setter
@NoArgsConstructor
public class Tributo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(length = 200)
    private String descripcion;
}
