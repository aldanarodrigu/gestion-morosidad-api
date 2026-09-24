package com.intendencia.gestion_morosidad_api.modules.segmento.repository;

import com.intendencia.gestion_morosidad_api.modules.segmento.entity.ConfiguracionSegmento;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionSegmentoRepository extends JpaRepository<ConfiguracionSegmento, Long> {

    @EntityGraph(attributePaths = "segmento")
    List<ConfiguracionSegmento> findByActivaTrueOrderByDiasDesdeAsc();

    @EntityGraph(attributePaths = "segmento")
    List<ConfiguracionSegmento> findAllByOrderByFechaCreacionDesc();
}
