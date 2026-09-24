package com.intendencia.gestion_morosidad_api.modules.deuda.repository;

import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Las consultas traen padrón y contribuyente en la misma query (EntityGraph)
 * para evitar N+1 al armar las respuestas.
 */
public interface DeudaRepository extends JpaRepository<Deuda, Long>, JpaSpecificationExecutor<Deuda> {

    @Override
    @EntityGraph(attributePaths = {"padron", "padron.contribuyente", "segmentoMora"})
    Page<Deuda> findAll(Specification<Deuda> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"padron", "padron.contribuyente", "segmentoMora"})
    Optional<Deuda> findById(Long id);

    @EntityGraph(attributePaths = {"padron", "padron.contribuyente", "segmentoMora"})
    List<Deuda> findByPadronNumeroPadronOrderByDeudaDesdeAsc(String numeroPadron);

    Optional<Deuda> findByPadronCm(String cm);

    List<Deuda> findByEstadoNot(EstadoDeuda estado);

    long countByEstadoNot(EstadoDeuda estado);

    @Query("select max(d.fechaSincronizacion) from Deuda d")
    Optional<LocalDateTime> ultimaSincronizacion();

    /** Todas las deudas con su padrón, para la sincronización (un solo SELECT). */
    @EntityGraph(attributePaths = "padron")
    @Query("select d from Deuda d")
    List<Deuda> findAllConPadron();
}
