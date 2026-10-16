package com.intendencia.gestion_morosidad_api.modules.deuda.dto;

import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;

/**
 * Filtros opcionales del listado de deudas.
 *
 * @param contribuyente texto contenido en el nombre, documento o CM
 * @param localidad texto contenido en el nombre de la localidad (sin distinguir mayúsculas)
 * @param segmentoId id del segmento de mora asignado
 * @param conContacto true: con al menos un contacto con valor; false: sin contacto; null: todos
 */
public record DeudaFiltro(EstadoDeuda estado, String padron, String contribuyente, String localidad, Long segmentoId, String tributo, Boolean conContacto, String tipoPadron, OrigenDatos origen) {
    public DeudaFiltro(EstadoDeuda estado, String padron, String contribuyente, String localidad, Long segmentoId, String tributo, Boolean conContacto, String tipoPadron) {
        this(estado, padron, contribuyente, localidad, segmentoId, tributo, conContacto, tipoPadron, null);
    }
}
