package com.intendencia.gestion_morosidad_api.config;

import com.intendencia.gestion_morosidad_api.modules.auth.PoliticaPassword;
import com.intendencia.gestion_morosidad_api.modules.usuario.Rol;
import com.intendencia.gestion_morosidad_api.modules.usuario.Usuario;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;

@Configuration
public class DataInitializer {
    // La clave local conocida debe cambiarse en el primer ingreso, pero no vence: una base de desarrollo
    // puede quedar días sin usarse y no debe perder el acceso al administrador.
    static final Instant CAMBIO_SIN_VENCIMIENTO = Instant.parse("9999-12-31T23:59:59Z");

    @Bean
    CommandLineRunner initAdmin(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            Environment ambiente,
            @Value("${ADMIN_BOOTSTRAP_PASSWORD:}") String passwordInicial
    ) {
        return args -> {

            // Solo en una base sin usuarios: si el admin se renombra, no se recrea con la contraseña por defecto.
            if (usuarioRepository.count() == 0) {

                String password = passwordInicial;
                boolean claveConocida = password.isEmpty() && !ambiente.matchesProfiles("prod");
                if (claveConocida) {
                    password = "admin123";
                } else {
                    if (password.isEmpty()) {
                        throw new IllegalStateException("Definí ADMIN_BOOTSTRAP_PASSWORD para crear el administrador inicial.");
                    }
                    PoliticaPassword.validarNueva(password);
                }

                Usuario admin = new Usuario();

                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode(password));
                admin.setRoles(java.util.Set.of(Rol.ADMINISTRADOR));
                admin.setActivo(true);
                if (claveConocida) admin.setPasswordTemporalHasta(CAMBIO_SIN_VENCIMIENTO);

                usuarioRepository.save(admin);
            }
        };
    }
}