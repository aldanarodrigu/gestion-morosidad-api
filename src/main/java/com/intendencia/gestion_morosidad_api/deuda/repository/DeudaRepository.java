package com.intendencia.gestion_morosidad_api.deuda.repository;

import com.intendencia.gestion_morosidad_api.deuda.entity.Deuda;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Las consultas traen padrón y contribuyente en la misma query (EntityGraph)
 * para evitar N+1 al armar las respuestas.
 */
public interface DeudaRepository extends JpaRepository<Deuda, Long>, JpaSpecificationExecutor<Deuda> {

    @Override
    @EntityGraph(attributePaths = {"padron", "padron.contribuyente"})
    Page<Deuda> findAll(Specification<Deuda> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"padron", "padron.contribuyente"})
    Optional<Deuda> findById(Long id);

    @EntityGraph(attributePaths = {"padron", "padron.contribuyente"})
    List<Deuda> findByPadronNumeroPadronOrderByDeudaDesdeAsc(String numeroPadron);

    Optional<Deuda> findByPadronCm(String cm);
}
