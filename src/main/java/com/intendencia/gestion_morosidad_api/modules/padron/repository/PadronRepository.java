package com.intendencia.gestion_morosidad_api.modules.padron.repository;

import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PadronRepository extends JpaRepository<Padron, Long>, JpaSpecificationExecutor<Padron> {

    @Query("select distinct trim(p.tipoPadron) from Padron p where p.tipoPadron is not null and trim(p.tipoPadron) <> '' order by trim(p.tipoPadron)")
    List<String> listarTiposPadron();

    @EntityGraph(attributePaths = "contribuyente")
    Optional<Padron> findByCmAndOrigen(String cm, OrigenDatos origen);

    default Optional<Padron> findByCm(String cm) {
        return findByCmAndOrigen(cm, OrigenDatos.INTENDENCIA);
    }

    @EntityGraph(attributePaths = "contribuyente")
    List<Padron> findAllByOrigen(OrigenDatos origen);

    /** El número de padrón se repite entre localidades: puede devolver varios padrones. */
    @EntityGraph(attributePaths = "contribuyente")
    List<Padron> findByNumeroPadronOrderByLocalidadAscCmAsc(String numeroPadron);

    @EntityGraph(attributePaths = "contribuyente")
    List<Padron> findByContribuyenteOrderByNumeroPadronAsc(Contribuyente contribuyente);

    /** Listado paginado de contribuyentes (ver {@link PadronSpecifications}). */
    @Override
    @EntityGraph(attributePaths = "contribuyente")
    Page<Padron> findAll(Specification<Padron> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "contribuyente")
    List<Padron> findAll();
}
