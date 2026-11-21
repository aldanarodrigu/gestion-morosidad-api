package com.intendencia.gestion_morosidad_api.modules.auth;

import com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException;
import java.nio.charset.StandardCharsets;

public final class PoliticaPassword {
    private PoliticaPassword() {}

    public static void validarNueva(String password) {
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 15) {
            throw new ReglaNegocioException("La nueva contraseña debe tener al menos 15 caracteres.");
        }
        validarLimiteBcrypt(password);
    }

    public static void validarLimiteBcrypt(String password) {
        if (superaLimiteBcrypt(password)) {
            throw new ReglaNegocioException("La contraseña no puede superar 72 bytes en UTF-8.");
        }
    }

    public static boolean superaLimiteBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > 72;
    }
}
