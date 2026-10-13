package com.intendencia.gestion_morosidad_api.modules.deuda.repository;

import com.intendencia.gestion_morosidad_api.modules.deuda.dto.DeudaFiltro;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

/** Pagina identidades en SQL antes de cargar sus deudas; nunca agrupa una página parcial. */
@Repository
public class CarteraAgrupadaRepository {
    @PersistenceContext private EntityManager em;

    public static Expression<String> clave(Root<Deuda> root, CriteriaBuilder cb) {
        var persona = root.get("padron").get("contribuyente");
        Expression<String> documento = cb.trim(persona.<String>get("documento"));
        return cb.<String>selectCase()
                .when(cb.and(cb.isNotNull(documento), cb.notEqual(documento, "")), cb.concat("DOC:", documento))
                .otherwise(cb.concat("ID:", persona.get("id").as(String.class)));
    }

    public Page<String> buscarGrupos(DeudaFiltro filtro, Pageable pageable) {
        var cb = em.getCriteriaBuilder();
        var count = cb.createQuery(Long.class);
        var cr = count.from(Deuda.class);
        count.where(DeudaSpecifications.conFiltro(filtro).toPredicate(cr, count, cb));
        count.distinct(false);
        count.select(cb.countDistinct(clave(cr, cb)));
        long total = em.createQuery(count).getSingleResult();
        if (total == 0) return new PageImpl<>(List.of(), pageable, 0);

        var query = cb.createQuery(String.class);
        var root = query.from(Deuda.class);
        var key = clave(root, cb);
        query.where(DeudaSpecifications.conFiltro(filtro).toPredicate(root, query, cb));
        query.distinct(false);
        query.select(key).groupBy(key);
        List<Order> ordenes = new ArrayList<>();
        for (Sort.Order orden : pageable.getSort()) {
            Expression<?> campo = switch (orden.getProperty()) {
                case "importe" -> cb.sum(root.<BigDecimal>get("importe"));
                case "padron.contribuyente.nombre", "nombre" -> cb.least(root.get("padron").get("contribuyente").<String>get("nombre"));
                default -> cb.least(root.<LocalDate>get("deudaDesde"));
            };
            ordenes.add(orden.isAscending() ? cb.asc(campo) : cb.desc(campo));
        }
        if (ordenes.isEmpty()) ordenes.add(cb.asc(cb.least(root.<LocalDate>get("deudaDesde"))));
        ordenes.add(cb.asc(key)); // Desempate estable entre páginas.
        query.orderBy(ordenes);
        var consulta = em.createQuery(query);
        consulta.setFirstResult(Math.toIntExact(pageable.getOffset()));
        consulta.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(consulta.getResultList(), pageable, total);
    }
}
