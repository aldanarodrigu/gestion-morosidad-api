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
    // Reserva IDs de a 1000 (la secuencia avanza de a 1000): permite enviar los INSERT en lotes
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "contribuyentes_seq")
    @SequenceGenerator(name = "contribuyentes_seq", sequenceName = "contribuyentes_id_seq", allocationSize = 1000)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private String documento;

    @OneToMany(mappedBy = "contribuyente", fetch = FetchType.LAZY)
    private List<Contacto> contactos = new ArrayList<>();
}
