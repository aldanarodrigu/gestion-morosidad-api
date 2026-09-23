package com.intendencia.gestion_morosidad_api.modules.padron.repository;

import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PadronRepository extends JpaRepository<Padron, Long> {

    Optional<Padron> findByCm(String cm);

    Optional<Padron> findByNumeroPadron(String numeroPadron);
}