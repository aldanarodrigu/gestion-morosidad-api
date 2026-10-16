package com.intendencia.gestion_morosidad_api.integration.intendencia.common;

import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.*;
import java.time.LocalDate;

/** Contrato normalizado de sincronización. GeoPat comparte los campos de facturas de GeoPagos. */
public interface IntendenciaClient {
    IntendenciaApiResponse<GeoPagosFacturaPendienteDto> obtenerFacturasPendientes();
    IntendenciaApiResponse<GeoPagosFacturaCanceladaDto> obtenerFacturasCanceladas(LocalDate desde, LocalDate hasta);
    IntendenciaApiResponse<GeoPagosContribuyenteDto> obtenerContribuyentes();
}
