package com.intendencia.gestion_morosidad_api.modules.contacto.entity;

import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contactos")
@Getter
@Setter
@NoArgsConstructor
public class Contacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_contacto", nullable = false)
    private TipoContacto tipoContacto;

    @Column(nullable = false)
    private String valor;

    @Column(name = "es_de_contribuyente", nullable = false)
    private Boolean esDeContribuyente;

    @Column(nullable = false)
    private String origen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contribuyente_id", nullable = false)
    private Contribuyente contribuyente;
}