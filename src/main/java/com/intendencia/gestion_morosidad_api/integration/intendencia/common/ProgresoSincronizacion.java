package com.intendencia.gestion_morosidad_api.integration.intendencia.common;

/** Contadores de la etapa actual; los totales permanecen null cuando la fuente no los informa. */
public record ProgresoSincronizacion(
        String etapa, int paginasDescargadas, Integer paginaActual, long registrosDescargados,
        Long totalRegistros, Integer totalPaginas, int intento, String detalle) {
    public static ProgresoSincronizacion preparando() {
        return new ProgresoSincronizacion("PREPARANDO",0,null,0,null,null,1,"Preparando sincronización.");
    }
}
