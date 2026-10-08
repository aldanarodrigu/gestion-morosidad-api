package com.intendencia.gestion_morosidad_api.modules.padron.repository;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PadronRepository extends JpaRepository<Padron, Long> {

    @EntityGraph(attributePaths = "contribuyente")
    Optional<Padron> findByCm(String cm);

    @EntityGraph(attributePaths = "contribuyente")
    Optional<Padron> findByNumeroPadron(String numeroPadron);

    @EntityGraph(attributePaths = "contribuyente")
    List<Padron> findByContribuyenteOrderByNumeroPadronAsc(Contribuyente contribuyente);

    @EntityGraph(attributePaths = "contribuyente")
    List<Padron> findByCmIn(Set<String> cms);

    @Override
    @EntityGraph(attributePaths = "contribuyente")
    List<Padron> findAll();
}
