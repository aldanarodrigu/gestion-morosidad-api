package com.intendencia.gestion_morosidad_api.modules.gestion;

import com.intendencia.gestion_morosidad_api.modules.accion.Accion;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "gestiones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Gestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultadoGestion resultado;

    @Column
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "deuda_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_gestion_deuda")
    )
    private Deuda deuda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "accion_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_gestion_accion")
    )
    private Accion accion;
}