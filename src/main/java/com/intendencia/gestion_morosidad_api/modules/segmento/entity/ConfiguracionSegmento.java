package com.intendencia.gestion_morosidad_api.modules.segmento.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Rango de días de atraso [diasDesde, diasHasta] que corresponde a un segmento.
 * Solo hay una configuración activa por segmento; las anteriores quedan como historial.
 */
@Entity
@Table(name = "configuraciones_segmento")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionSegmento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "segmento_mora_id", nullable = false)
    private SegmentoMora segmento;

    @Column(name = "dias_desde", nullable = false)
    private Integer diasDesde;

    /** null = sin tope superior. */
    @Column(name = "dias_hasta")
    private Integer diasHasta;

    @Column(nullable = false)
    private boolean activa = true;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public boolean contiene(long dias) {
        return dias >= diasDesde && (diasHasta == null || dias <= diasHasta);
    }

    /** Dos rangos se superponen si comparten al menos un día. */
    public boolean seSuperponeCon(ConfiguracionSegmento otra) {
        boolean estaTerminaAntes = diasHasta != null && diasHasta < otra.diasDesde;
        boolean otraTerminaAntes = otra.diasHasta != null && otra.diasHasta < diasDesde;
        return !estaTerminaAntes && !otraTerminaAntes;
    }
}
