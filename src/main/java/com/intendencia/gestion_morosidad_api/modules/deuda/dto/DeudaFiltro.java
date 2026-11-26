package com.intendencia.gestion_morosidad_api.modules.deuda.dto;

import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
import java.time.LocalDate;

/**
 * Filtros opcionales del listado de deudas.
 *
 * @param contribuyente texto contenido en el nombre, documento o CM
 * @param localidad texto contenido en el nombre de la localidad (sin distinguir mayúsculas)
 * @param segmentoId id del segmento de mora asignado
 * @param conContacto true: con al menos un contacto con valor; false: sin contacto; null: todos
 * @param desde deudas cuya deuda impaga más antigua (deudaDesde) es de esta fecha o posterior
 * @param hasta deudas cuya deuda impaga más antigua (deudaDesde) es de esta fecha o anterior
 */
public record DeudaFiltro(EstadoDeuda estado, String padron, String contribuyente, String localidad, Long segmentoId,
        String tributo, Boolean conContacto, String tipoPadron, OrigenDatos origen, LocalDate desde, LocalDate hasta) {

    public DeudaFiltro {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaNegocioException("La fecha hasta debe ser igual o posterior a la fecha desde.");
        }
    }

    public DeudaFiltro(EstadoDeuda estado, String padron, String contribuyente, String localidad, Long segmentoId, String tributo, Boolean conContacto, String tipoPadron, OrigenDatos origen) {
        this(estado, padron, contribuyente, localidad, segmentoId, tributo, conContacto, tipoPadron, origen, null, null);
    }

    public DeudaFiltro(EstadoDeuda estado, String padron, String contribuyente, String localidad, Long segmentoId, String tributo, Boolean conContacto, String tipoPadron) {
        this(estado, padron, contribuyente, localidad, segmentoId, tributo, conContacto, tipoPadron, null);
    }
}
