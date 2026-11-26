package com.intendencia.gestion_morosidad_api.integration.intendencia.common;

import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.*;
import java.time.LocalDate;

/** Contrato normalizado de sincronización. GeoPat comparte los campos de facturas de GeoPagos. */
public interface IntendenciaClient {
    IntendenciaApiResponse<GeoPagosFacturaPendienteDto> obtenerFacturasPendientes();
    IntendenciaApiResponse<GeoPagosFacturaPendienteDto> obtenerFacturasPendientes(LocalDate desde, LocalDate hasta);
    IntendenciaApiResponse<GeoPagosFacturaPendienteDto> obtenerFacturasPendientesPorCm(
            String cm, LocalDate desde, LocalDate hasta);
    IntendenciaApiResponse<GeoPagosFacturaCanceladaDto> obtenerFacturasCanceladas(LocalDate desde, LocalDate hasta);
    IntendenciaApiResponse<GeoPagosContribuyenteDto> obtenerContribuyentes();
}
