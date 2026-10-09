package com.intendencia.gestion_morosidad_api.modules.padron.entity;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "padrones")
@Getter
@Setter
@NoArgsConstructor
public class Padron {

    @Id
    // Reserva IDs de a 1000 (la secuencia avanza de a 1000): permite enviar los INSERT en lotes
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "padrones_seq")
    @SequenceGenerator(name = "padrones_seq", sequenceName = "padrones_id_seq", allocationSize = 1000)
    private Long id;

    @Column(nullable = false, unique = true)
    private String cm;

    /** No es único: cada localidad numera sus padrones por separado. El identificador es el CM. */
    @Column(name = "numero_padron", nullable = false)
    private String numeroPadron;

    @Column(name = "tipo_padron")
    private String tipoPadron;

    private String localidad;

    private String block;

    private String unidad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contribuyente_id", nullable = false)
    private Contribuyente contribuyente;
}