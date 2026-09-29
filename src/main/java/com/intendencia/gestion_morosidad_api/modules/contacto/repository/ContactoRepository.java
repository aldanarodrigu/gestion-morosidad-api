package com.intendencia.gestion_morosidad_api.modules.contacto.repository;

import com.intendencia.gestion_morosidad_api.modules.contacto.entity.Contacto;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactoRepository extends JpaRepository<Contacto, Long> {

    List<Contacto> findByContribuyente(Contribuyente contribuyente);

    List<Contacto> findByContribuyenteIn(List<Contribuyente> contribuyentes);

    Optional<Contacto> findByIdAndContribuyente(
            Long id,
            Contribuyente contribuyente
    );
}
