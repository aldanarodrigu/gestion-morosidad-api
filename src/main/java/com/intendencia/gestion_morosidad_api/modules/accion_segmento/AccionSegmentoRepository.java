package com.intendencia.gestion_morosidad_api.modules.accion_segmento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccionSegmentoRepository
        extends JpaRepository<AccionSegmento, Long> {

    List<AccionSegmento> findBySegmentoMora_Id(Long segmentoId);

    List<AccionSegmento> findByAccion_Id(Long accionId);

    boolean existsByAccion_IdAndSegmentoMora_Id(
            Long accionId,
            Long segmentoId
    );
}