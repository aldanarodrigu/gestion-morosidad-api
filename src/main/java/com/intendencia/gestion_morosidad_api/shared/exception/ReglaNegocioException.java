package com.intendencia.gestion_morosidad_api.shared.exception;

/** Datos válidos en formato pero que violan una regla del negocio. Se traduce a HTTP 400. */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
