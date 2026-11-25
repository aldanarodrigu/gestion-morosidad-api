package com.intendencia.gestion_morosidad_api.config;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Swagger UI y OpenAPI quedan disponibles en desarrollo y no se publican con el perfil prod. */
@WebMvcTest(DocumentacionApiPerfilTest.Sonda.class)
@Import({SecurityConfig.class, DocumentacionApiPerfilTest.Sonda.class})
// @WebMvcTest no carga springdoc: se importa su configuración para probar el comportamiento real.
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class, SwaggerConfig.class, SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class})
class DocumentacionApiPerfilTest {
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;

    @RestController static class Sonda {
        @GetMapping("/api/qa-docs") String sonda() { return "ok"; }
    }

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
