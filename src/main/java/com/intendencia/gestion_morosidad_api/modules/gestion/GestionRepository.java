package com.intendencia.gestion_morosidad_api.modules.gestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface GestionRepository extends JpaRepository<Gestion, Long> {

    @Query("""
        SELECT g.deuda.id, COUNT(g)
        FROM Gestion g
        WHERE g.deuda.id IN :deudaIds
        GROUP BY g.deuda.id
    """)
    List<Object[]> contarPorDeudaIds(
            @Param("deudaIds") Collection<Long> deudaIds
    );

}
