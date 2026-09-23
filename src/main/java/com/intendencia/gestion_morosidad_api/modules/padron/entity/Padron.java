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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String cm;

    @Column(name = "numero_padron", nullable = false, unique = true)
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