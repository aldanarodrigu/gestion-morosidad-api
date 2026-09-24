package com.intendencia.gestion_morosidad_api.modules.deuda.entity;

import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Deuda vencida de un padrón, cargada desde GeoPagos (/facturas/pendientes).
 * Los datos del padrón (CM, número, localidad, tipo) y del contribuyente se obtienen a través de {@link #padron}.
 */
@Entity
@Table(name = "deuda")
@Getter
@Setter
@NoArgsConstructor
public class Deuda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "padron_id", nullable = false)
    private Padron padron;

    /**
     * Tributos que componen la deuda (TRIBUTOS_DEUDA de GeoPagos).
     * BatchSize: en los listados carga los tributos de varias deudas en una sola consulta (evita N+1
     * sin hacer fetch de la colección, que rompería la paginación).
     */
    @ManyToMany
    @JoinTable(
            name = "deuda_tributo",
            joinColumns = @JoinColumn(name = "deuda_id"),
            inverseJoinColumns = @JoinColumn(name = "tributo_id"))
    @BatchSize(size = 50)
    private Set<Tributo> tributos = new HashSet<>();

    @Column(precision = 15, scale = 2)
    private BigDecimal importe;

    /** Vencimiento más antiguo impago: base para calcular días de atraso y segmento de mora. */
    @Column(name = "deuda_desde")
    private LocalDate deudaDesde;

    @Column(name = "ultimo_vencimiento")
    private LocalDate ultimoVencimiento;

    @Column(name = "anios_deuda", length = 200)
    private String aniosDeuda;

    @Column(nullable = false)
    private boolean convenio;

    /** Asignado automáticamente según los días de atraso y los rangos configurados (null si ninguno aplica). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segmento_mora_id")
    private SegmentoMora segmentoMora;

    /** Último cobro registrado en GeoPagos para el padrón (/facturas/canceladas). */
    @Column(name = "fecha_ultimo_cobro")
    private LocalDate fechaUltimoCobro;

    @Column(name = "importe_ultimo_cobro", precision = 15, scale = 2)
    private BigDecimal importeUltimoCobro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDeuda estado = EstadoDeuda.PENDIENTE;

    @Column(name = "fecha_sincronizacion")
    private LocalDateTime fechaSincronizacion;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
}
