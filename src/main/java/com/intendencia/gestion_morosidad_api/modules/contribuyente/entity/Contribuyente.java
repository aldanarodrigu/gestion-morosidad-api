package com.intendencia.gestion_morosidad_api.modules.contribuyente.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contribuyentes")
@Getter
@Setter
@NoArgsConstructor
public class Contribuyente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private String documento;
}
