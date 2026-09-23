package com.intendencia.gestion_morosidad_api.deuda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Deuda vencida de un padrón. Cada fila corresponde a un CM de GeoPagos (/facturas/pendientes).
 * CM, padrón y documento se guardan como texto: son identificadores, no números operables.
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

    @Column(nullable = false, unique = true, length = 30)
    private String cm;

    @Column(name = "numero_padron", nullable = false, length = 30)
    private String numeroPadron;

    @Column(length = 30)
    private String block;

    @Column(length = 30)
    private String unidad;

    @Column(length = 100)
    private String localidad;

    /** Tipo de padrón (CIU, CIR, CNV, SEM, FAC...): determina la clasificación del deudor. */
    @Column(length = 10)
    private String padtipo;

    @Column(name = "contribuyente_nombre", length = 200)
    private String contribuyenteNombre;

    @Column(name = "contribuyente_documento", length = 30)
    private String contribuyenteDocumento;

    /** Códigos de tributo separados por coma, tal como llegan de GeoPagos (TRIBUTOS_DEUDA). */
    @Column(length = 200)
    private String tributos;

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
