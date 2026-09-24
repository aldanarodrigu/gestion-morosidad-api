package com.intendencia.gestion_morosidad_api.modules.segmento.repository;

import com.intendencia.gestion_morosidad_api.modules.segmento.entity.SegmentoMora;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SegmentoMoraRepository extends JpaRepository<SegmentoMora, Long> {

    List<SegmentoMora> findAllByOrderByOrdenAsc();

    boolean existsByNombreIgnoreCase(String nombre);
}
