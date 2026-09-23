package com.intendencia.gestion_morosidad_api.shared.exception;

/** Se traduce a HTTP 404 en {@link GlobalExceptionHandler}. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
