package com.intendencia.gestion_morosidad_api.modules.accion_segmento;

import com.intendencia.gestion_morosidad_api.modules.accion.Accion;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "acciones_segmentos", uniqueConstraints = {@UniqueConstraint(name = "uk_accion_segmento", columnNames = {"accion_id", "segmento_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccionSegmento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accion_id",nullable = false, foreignKey = @ForeignKey(name = "fk_accion_segmento_accion"))
    private Accion accion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "segmento_id", nullable = false, foreignKey = @ForeignKey(name = "fk_accion_segmento_segmento"))
    private SegmentoMora segmentoMora;
}