package com.intendencia.gestion_morosidad_api.modules.deuda.repository;

import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
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

    @Query("""
            select distinct trim(d.padron.tipoPadron) from Deuda d
            where d.estado <> :excluido and d.importe > 0
              and d.padron.tipoPadron is not null and trim(d.padron.tipoPadron) <> ''
            order by trim(d.padron.tipoPadron)
            """)
    List<String> listarTiposPadron(EstadoDeuda excluido);

    @Override
    @EntityGraph(attributePaths = {"padron", "padron.contribuyente", "segmentoMora"})
    Page<Deuda> findAll(Specification<Deuda> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"padron", "padron.contribuyente", "segmentoMora"})
    Optional<Deuda> findById(Long id);

    @EntityGraph(attributePaths = {"padron", "padron.contribuyente", "segmentoMora"})
    List<Deuda> findByPadronNumeroPadronOrderByDeudaDesdeAsc(String numeroPadron);

    Optional<Deuda> findByPadronCmAndPadronOrigen(String cm, OrigenDatos origen);

    default Optional<Deuda> findByPadronCm(String cm) {
        return findByPadronCmAndPadronOrigen(cm, OrigenDatos.INTENDENCIA);
    }

    List<Deuda> findByEstadoNot(EstadoDeuda estado);

    @EntityGraph(attributePaths = "padron")
    @Query("select d from Deuda d where d.estado <> :excluido and (:tipoPadron is null or trim(d.padron.tipoPadron) = :tipoPadron)")
    List<Deuda> findParaRecalcularSegmentos(EstadoDeuda excluido, String tipoPadron);

    long countByEstadoNot(EstadoDeuda estado);

    @Query("select max(d.fechaSincronizacion) from Deuda d")
    Optional<LocalDateTime> ultimaSincronizacion();

    @Query("select max(d.fechaSincronizacion) from Deuda d where d.padron.origen = :origen")
    Optional<LocalDateTime> ultimaSincronizacionPorOrigen(OrigenDatos origen);

    long countByPadronOrigenAndEstadoNot(OrigenDatos origen, EstadoDeuda estado);

    @EntityGraph(attributePaths = "padron")
    @Query("select d from Deuda d where d.padron.origen = :origen")
    List<Deuda> findAllConPadronPorOrigen(OrigenDatos origen);

    @EntityGraph(attributePaths = {"padron", "padron.contribuyente", "segmentoMora"})
    List<Deuda> findByPadronCmAndPadronOrigenOrderByDeudaDesdeAsc(String cm, OrigenDatos origen);

    /** Todas las deudas con su padrón, para la sincronización (un solo SELECT). */
    @EntityGraph(attributePaths = "padron")
    @Query("select d from Deuda d")
    List<Deuda> findAllConPadron();

    @EntityGraph(attributePaths = {
            "padron",
            "padron.contribuyente",
            "segmentoMora"
    })
    default List<Deuda> findByPadronCmOrderByDeudaDesdeAsc(String cm) {
        return findByPadronCmAndPadronOrigenOrderByDeudaDesdeAsc(cm, OrigenDatos.INTENDENCIA);
    }

    List<Deuda> findByPadron_Contribuyente_IdOrderByDeudaDesdeAsc(
            Long contribuyenteId
    );
}
