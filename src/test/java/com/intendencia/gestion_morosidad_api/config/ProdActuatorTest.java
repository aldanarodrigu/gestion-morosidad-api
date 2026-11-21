package com.intendencia.gestion_morosidad_api.config;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@EnabledIfEnvironmentVariable(named="APP_TEST_DB_URL",matches=".+")
@TestPropertySource(locations={"classpath:application.properties","classpath:application-prod.properties"})
@SpringBootTest(properties={"spring.config.name=arranque-aislado","spring.config.import=","sincronizacion.automatica.habilitada=false","ADMIN_BOOTSTRAP_PASSWORD=clave-administrador-prueba","geopagos.username=","geopagos.password="})
@ActiveProfiles("prod") @AutoConfigureMockMvc
class ProdActuatorTest {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){String url=System.getenv("APP_TEST_DB_URL");assertThat(url).matches("jdbc:postgresql://(localhost|127[.]0[.]0[.]1):[0-9]+/app_test_[a-z0-9_]+");r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->"morosidad");r.add("spring.datasource.password",()->"morosidad-local");r.add("jwt.secret",()->Base64.getEncoder().encodeToString(new byte[32]));}
 @Autowired MockMvc mvc;@Autowired Environment env;
 @Test void healthPublicoNoExponeDetallesDeLaBase() throws Exception {mvc.perform(get("/actuator/health").with(anonymous())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.components").doesNotExist()).andExpect(jsonPath("$.details").doesNotExist());}
 @ParameterizedTest @ValueSource(strings={"env","beans","configprops","heapdump"}) void endpointsSensiblesNoExpuestosNiAlAdministrador(String endpoint) throws Exception {mvc.perform(get("/actuator/"+endpoint).with(user("admin").roles("ADMINISTRADOR"))).andExpect(status().isNotFound());}
 @Test void perfilProdMantieneOcultosErroresYExponeSoloHealth(){assertThat(env.getActiveProfiles()).contains("prod");assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");assertThat(env.getProperty("management.endpoint.health.show-details")).isEqualTo("never");assertThat(env.getProperty("server.error.include-stacktrace")).isEqualTo("never");}
}
