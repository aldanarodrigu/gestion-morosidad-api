package com.intendencia.gestion_morosidad_api.shared.exception;

public class LoginBloqueadoException extends RuntimeException {
    private final long reintentarEnSegundos;

    public LoginBloqueadoException(long reintentarEnSegundos) {
        super("Demasiados intentos de inicio de sesión. Volvé a intentarlo en " + reintentarEnSegundos + " s.");
        this.reintentarEnSegundos = reintentarEnSegundos;
    }

    public long reintentarEnSegundos() {
        return reintentarEnSegundos;
    }
}
