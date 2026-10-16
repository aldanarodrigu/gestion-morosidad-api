package com.intendencia.gestion_morosidad_api.modules.tributo.repository;

import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;

public interface TributoRepository extends JpaRepository<Tributo, Long> {

    /** Ordena como número ("801" antes que "3802") aunque el código sea texto. */
    @Query("select t from Tributo t order by t.origen, length(t.codigo), t.codigo")
    List<Tributo> findAllOrdenados();

    @Query(value = "select exists(select 1 from deuda_tributo where tributo_id = :id)", nativeQuery = true)
    boolean tieneDeudas(@org.springframework.data.repository.query.Param("id") Long id);

    @Query(value = "select exists(select 1 from configuraciones_segmento where tributo_id = :id)", nativeQuery = true)
    boolean tieneConfiguraciones(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Tributo> findByCodigoAndOrigen(String codigo, OrigenDatos origen);

    default Optional<Tributo> findByCodigo(String codigo) {
        return findByCodigoAndOrigen(codigo, OrigenDatos.INTENDENCIA);
    }

    List<Tributo> findAllByOrigen(OrigenDatos origen);
}
