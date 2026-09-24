package com.intendencia.gestion_morosidad_api.shared.exception;

/** Falla al consultar un sistema externo (API GeoPagos). Se traduce a HTTP 502. */
public class IntegracionExternaException extends RuntimeException {

    public IntegracionExternaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public IntegracionExternaException(String mensaje) {
        super(mensaje);
    }
}
