package com.intendencia.gestion_morosidad_api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(@Value("${jwt.secret}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    // Consulta los roles actuales: las bajas y cambios de permisos se aplican con tokens ya emitidos.
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            UsuarioRepository usuarios) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var usuario = usuarios.findByUsername(jwt.getSubject())
                    .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                    .orElseThrow(() -> new OAuth2AuthenticationException(
                            new OAuth2Error("invalid_token"),
                            "Usuario inactivo o inexistente"));
            return usuario.getRoles().stream()
                    .map(rol -> (GrantedAuthority)
                            new SimpleGrantedAuthority("ROLE_" + rol.name()))
                    .toList();
        });
        return converter;
    }
}
