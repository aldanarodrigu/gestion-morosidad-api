package com.intendencia.gestion_morosidad_api.modules.contribuyente.repository;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContribuyenteRepository
        extends JpaRepository<Contribuyente, Long> {
}
