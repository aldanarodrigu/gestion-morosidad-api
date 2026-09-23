package com.intendencia.gestion_morosidad_api.deuda.repository;

import com.intendencia.gestion_morosidad_api.deuda.entity.Deuda;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DeudaRepository extends JpaRepository<Deuda, Long>, JpaSpecificationExecutor<Deuda> {

    List<Deuda> findByNumeroPadronOrderByDeudaDesdeAsc(String numeroPadron);

    Optional<Deuda> findByCm(String cm);
}
