package com.intendencia.gestion_morosidad_api.modules.tributo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
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
    // Reserva IDs de a 1000 (la secuencia avanza de a 1000): permite enviar los INSERT en lotes
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tributos_seq")
    @SequenceGenerator(name = "tributos_seq", sequenceName = "tributos_id_seq", allocationSize = 1000)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(length = 200)
    private String descripcion;
}
