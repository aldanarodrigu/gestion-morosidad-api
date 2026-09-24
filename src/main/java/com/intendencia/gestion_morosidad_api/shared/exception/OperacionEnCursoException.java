package com.intendencia.gestion_morosidad_api.shared.exception;

/** Se intentó iniciar un proceso que ya está corriendo. Se traduce a HTTP 409. */
public class OperacionEnCursoException extends RuntimeException {

    public OperacionEnCursoException(String mensaje) {
        super(mensaje);
    }
}
