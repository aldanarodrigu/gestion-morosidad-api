package com.intendencia.gestion_morosidad_api.config;

import com.intendencia.gestion_morosidad_api.modules.auth.AuthController;
import com.intendencia.gestion_morosidad_api.modules.auth.AuthService;
import com.intendencia.gestion_morosidad_api.modules.usuario.UsuarioRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Swagger UI y OpenAPI quedan disponibles en desarrollo y no se publican con el perfil prod. */
// Usa un controlador real: un @RestController anidado acá se registraría en los contextos de otros tests
// (los @Test están en las clases anidadas, así que el filtro de tipos de test no excluiría esta clase).
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
// @WebMvcTest no carga springdoc: se importa su configuración para probar el comportamiento real.
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class, SwaggerConfig.class, SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class})
class DocumentacionApiPerfilTest {
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;
    @MockitoBean AuthService authService;
    @MockitoBean UsuarioRepository usuarios;

    @Nested
    class Desarrollo {
        @Autowired MockMvc mvc;

        @Test void documentacionDisponibleSinSesion() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
            mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @ActiveProfiles("prod")
    class Produccion {
        @Autowired MockMvc mvc;

        @Test void documentacionNoSePublica() throws Exception {
            for (String ruta : new String[]{"/v3/api-docs", "/v3/api-docs/swagger-config",
                    "/swagger-ui.html", "/swagger-ui/index.html"}) {
                mvc.perform(get(ruta)).andExpect(status().isNotFound());
            }
        }
    }
}
