package com.intendencia.gestion_morosidad_api.modules.padron.repository;

import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PadronRepository extends JpaRepository<Padron, Long> {

    @EntityGraph(attributePaths = "contribuyente")
    Optional<Padron> findByCm(String cm);

    @EntityGraph(attributePaths = "contribuyente")
    Optional<Padron> findByNumeroPadron(String numeroPadron);

    @Override
    @EntityGraph(attributePaths = "contribuyente")
    List<Padron> findAll();
}
