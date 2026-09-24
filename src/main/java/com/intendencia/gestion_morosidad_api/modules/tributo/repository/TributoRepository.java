package com.intendencia.gestion_morosidad_api.modules.tributo.repository;

import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TributoRepository extends JpaRepository<Tributo, Long> {

    /** Ordena como número ("801" antes que "3802") aunque el código sea texto. */
    @Query("select t from Tributo t order by length(t.codigo), t.codigo")
    List<Tributo> findAllOrdenados();

    Optional<Tributo> findByCodigo(String codigo);
}
