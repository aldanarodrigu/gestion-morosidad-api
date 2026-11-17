package com.intendencia.gestion_morosidad_api.migration;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Requiere la copia anonimizada creada por scripts/test_copia_actual.py. Nunca limpia public. */
@EnabledIfEnvironmentVariable(named="FLYWAY_COPIA_TEST_DB_URL", matches=".+")
class FlywayCopiaActualTest {
    JdbcTemplate jdbc;
    DriverManagerDataSource ds;
    @BeforeEach void conectarSoloCopia() {
        String url=System.getenv("FLYWAY_COPIA_TEST_DB_URL");
        assertThat(url).matches("jdbc:postgresql://(localhost|127[.]0[.]0[.]1):[0-9]+/flyway_copia_[a-z0-9_]+");
        ds=new DriverManagerDataSource(url,"morosidad","morosidad-local");
        jdbc=new JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contribuyentes",Long.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE username NOT LIKE 'usuario_prueba_%'",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contribuyentes WHERE nombre NOT LIKE 'Persona de prueba %'",Long.class)).isZero();
    }
    Map<String,List<Map<String,Object>>> huellas() {
        Map<String,List<Map<String,Object>>> result=new TreeMap<>();
        for(String tabla:jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename",String.class)) {
            assertThat(tabla).matches("[a-z_]+");
            result.put(tabla,jdbc.queryForList("SELECT count(*) n, coalesce(sum(hashtextextended(t::text,0)::numeric),0) huella FROM public."+tabla+" t"));
        }
        return result;
    }
    @Test void validaCopiaActualSinReaplicarNiAlterarNingunaFila() {
        var antes=huellas();
        var f=Flyway.configure().dataSource(ds).defaultSchema("public").cleanDisabled(true).load();
        assertThat(f.validateWithResult().validationSuccessful).isTrue();
        assertThat(f.migrate().migrationsExecuted).isZero();
        assertThat(f.info().pending()).isEmpty();
        assertThat(huellas()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario_roles r LEFT JOIN usuarios u ON u.id=r.usuario_id WHERE u.id IS NULL",Long.class)).isZero();
    }
    @Test void migraUsuariosAnonimizadosActualesDesdeEsquemaDeRolUnico() {
        // Reconstrucción explícita del esquema anterior; no se presenta como un respaldo histórico.
        String schema="legacy_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE SCHEMA "+schema);
        var antigua=new DriverManagerDataSource(System.getenv("FLYWAY_COPIA_TEST_DB_URL")+"?currentSchema="+schema,"morosidad","morosidad-local");
        var legacy=new JdbcTemplate(antigua);
        try {
            Flyway.configure().dataSource(antigua).defaultSchema(schema).target("202610121930").cleanDisabled(true).load().migrate();
            // Cada usuario antiguo admite un rol. Si hoy tiene varios se proyecta el primero ordenado.
            var usuarios=jdbc.queryForList("SELECT u.id,u.username,u.password,u.activo,min(r.rol) rol FROM public.usuarios u JOIN public.usuario_roles r ON r.usuario_id=u.id GROUP BY u.id ORDER BY u.id");
            assertThat(usuarios).isNotEmpty();
            assertThat(usuarios).hasSize(jdbc.queryForObject("SELECT count(*) FROM public.usuarios",Integer.class));
            for(var u:usuarios) legacy.update("INSERT INTO usuarios(id,username,password,activo,rol) VALUES(?,?,?,?,?)",u.get("id"),u.get("username"),u.get("password"),u.get("activo"),u.get("rol"));
            Flyway.configure().dataSource(antigua).defaultSchema(schema).cleanDisabled(true).load().migrate();
            assertThat(legacy.queryForList("SELECT u.id,u.username,u.password,u.activo,r.rol FROM usuarios u JOIN usuario_roles r ON r.usuario_id=u.id ORDER BY u.id")).isEqualTo(usuarios);
            assertThat(legacy.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='usuarios' AND column_name='rol'",Long.class,schema)).isZero();
            long id=((Number)usuarios.get(0).get("id")).longValue();
            for(String rol:List.of("ADMINISTRADOR","NOTIFICADOR","ANALISTA")) legacy.update("INSERT INTO usuario_roles(usuario_id,rol) VALUES(?,?) ON CONFLICT DO NOTHING",id,rol);
            assertThat(legacy.queryForObject("SELECT count(*) FROM usuario_roles WHERE usuario_id=?",Long.class,id)).isEqualTo(3);
        } finally { jdbc.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
}
