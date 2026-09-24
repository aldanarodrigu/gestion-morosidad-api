package com.intendencia.gestion_morosidad_api.modules.segmento.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Segmento de morosidad (Mora Temprana, Inicial, Tardía). Sus rangos de días están en {@link ConfiguracionSegmento}. */
@Entity
@Table(name = "segmentos_mora")
@Getter
@Setter
@NoArgsConstructor
public class SegmentoMora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    /** Orden de presentación (1 = menor atraso). */
    @Column(nullable = false)
    private Integer orden;
}
