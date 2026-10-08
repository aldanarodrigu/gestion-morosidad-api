package com.intendencia.gestion_morosidad_api.modules.gestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GestionRepository extends JpaRepository<Gestion, Long> {
}
