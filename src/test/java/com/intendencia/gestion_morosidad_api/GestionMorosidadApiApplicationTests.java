package com.intendencia.gestion_morosidad_api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

/** Arranque real solo contra una base local desechable explícita; nunca carga el .env de trabajo. */
@EnabledIfEnvironmentVariable(named="APP_TEST_DB_URL",matches=".+")
@TestPropertySource(locations="classpath:application.properties")
@SpringBootTest(properties={"spring.config.name=arranque-aislado", "spring.config.import=", "sincronizacion.automatica.habilitada=false",
        "geopagos.username=", "geopagos.password="})
class GestionMorosidadApiApplicationTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        String url=System.getenv("APP_TEST_DB_URL");
        assertThat(url).as("Solo se permiten bases locales app_test_ creadas para esta prueba")
                .matches("jdbc:postgresql://(localhost|127[.]0[.]0[.]1):[0-9]+/app_test_[a-z0-9_]+");
        r.add("spring.datasource.url",()->url);
        r.add("spring.datasource.username",()->"morosidad");
        r.add("spring.datasource.password",()->"morosidad-local");
        r.add("jwt.secret",()->Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
    }
    @Autowired Environment entorno;
    @Autowired JdbcTemplate jdbc;

    @Test void arranqueAisladoAplicaMigracionesEInicializaSinSincronizacionExterna() {
        assertThat(entorno.getProperty("sincronizacion.automatica.habilitada",Boolean.class)).isFalse();
        assertThat(entorno.getProperty("spring.datasource.url")).isEqualTo(System.getenv("APP_TEST_DB_URL"));
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).startsWith("app_test_");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Long.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE username='admin'",Long.class)).isEqualTo(1);
        assertThat(entorno.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        var fuentes=((org.springframework.core.env.ConfigurableEnvironment)entorno).getPropertySources();
        assertThat(java.util.stream.StreamSupport.stream(fuentes.spliterator(),false).map(f->f.getName()).toList())
                .noneMatch(n->n.contains(".env"));
    }
}
