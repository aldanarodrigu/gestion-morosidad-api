package com.intendencia.gestion_morosidad_api.modules.contribuyente.entity;

import com.intendencia.gestion_morosidad_api.modules.contacto.entity.Contacto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "contribuyente", fetch = FetchType.LAZY)
    private List<Contacto> contactos = new ArrayList<>();
}
