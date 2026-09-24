package com.intendencia.gestion_morosidad_api.shared.exception;

/** Se traduce a HTTP 409 en {@link GlobalExceptionHandler}. */
public class RecursoYaExisteException extends RuntimeException {

    public RecursoYaExisteException(String mensaje) {
        super(mensaje);
    }
}