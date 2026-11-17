package com.intendencia.gestion_morosidad_api.migration;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Ejecuta SQL real en esquemas independientes de una base PostgreSQL desechable explícita. */
@EnabledIfEnvironmentVariable(named="FLYWAY_TEST_DB_URL",matches=".+")
class FlywayMigracionesTest {
    JdbcTemplate jdbc;
    DriverManagerDataSource dataSource;
    static final long ID=5001;

    @BeforeEach void crearEsquemaAislado() {
        String url=System.getenv("FLYWAY_TEST_DB_URL");
        // Nunca utiliza la base de desarrollo ni permite limpiar una base por accidente.
        assertThat(url).matches("jdbc:postgresql://(localhost|127[.]0[.]0[.]1):[0-9]+/flyway_revision_[a-z0-9_]+");
        String schema="flyway_test_"+UUID.randomUUID().toString().replace("-","");
        var admin=new DriverManagerDataSource(url,"morosidad","morosidad-local");
        new JdbcTemplate(admin).execute("CREATE SCHEMA "+schema);
        dataSource=new DriverManagerDataSource(url+"?currentSchema="+schema,"morosidad","morosidad-local");
        jdbc=new JdbcTemplate(dataSource);
    }
    Flyway flyway(String version) {
        var config=Flyway.configure().dataSource(dataSource).defaultSchema(jdbc.queryForObject("SELECT current_schema()",String.class))
                .locations("classpath:db/migration").cleanDisabled(true);
        if(version!=null)config.target(version);
        return config.load();
    }
    boolean tabla(String nombre) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=current_schema() AND table_name=?)",Boolean.class,nombre));
    }
    boolean columna(String tabla,String nombre) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name=? AND column_name=?)",Boolean.class,tabla,nombre));
    }
    long numero(String sql,Object...args) {return jdbc.queryForObject(sql,Long.class,args);}
    @Test void baseVaciaAplicaTodasLasMigracionesYValidaChecksums() {
        var f=flyway(null);int disponibles=f.info().all().length;
        try(var fuentes=java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/db/migration"))) {
            var scripts=fuentes.map(p->p.getFileName().toString()).filter(n->n.startsWith("V") && n.endsWith(".sql")).toList();
            assertThat(Arrays.stream(f.info().all()).map(m->m.getScript()).toList())
                    .as("El classpath no debe ocultar SQL faltantes ni conservar scripts borrados en target")
                    .containsExactlyInAnyOrderElementsOf(scripts);
        } catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}
        assertThat(disponibles).isPositive();assertThat(f.migrate().migrationsExecuted).isEqualTo(disponibles);
        assertThat(f.info().pending()).isEmpty();assertThat(f.validateWithResult().validationSuccessful).isTrue();
        assertThat(numero("SELECT count(*) FROM flyway_schema_history WHERE success")).isEqualTo(disponibles);
        for(String t:List.of("contribuyentes","padrones","deuda","contactos","gestiones","usuario_roles","capturas_cartera","captura_cuentas","configuracion_sincronizacion"))
            assertThat(tabla(t)).as(t).isTrue();
        assertThat(numero("SELECT anio_minimo FROM configuracion_sincronizacion WHERE id=1")).isEqualTo(2013);
        assertThat(numero("SELECT count(*) FROM segmentos_mora")).isEqualTo(3);
        assertThat(numero("SELECT count(*) FROM configuraciones_segmento WHERE activa AND tributo_id IS NULL AND tipo_padron IS NULL")).isEqualTo(3);
    }
    @Test void segundoArranqueNoReaplicaMigracionesNiDuplicaDatos() {
        var f=flyway(null);f.migrate();semilla();var antes=filas();long aplicadas=numero("SELECT count(*) FROM flyway_schema_history");
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
        assertThat(filas()).isEqualTo(antes);assertThat(numero("SELECT count(*) FROM flyway_schema_history")).isEqualTo(aplicadas);
        assertThat(flyway(null).validateWithResult().validationSuccessful).isTrue();
    }
    @ParameterizedTest
    @ValueSource(strings={"202610071300","202610121930","202610131200","202611121000","202611161000"})
    void actualizaVersionAnteriorConDatosSinPerderIdentidadesNiRelaciones(String version) {
        flyway(version).migrate();semilla();var antes=filas();
        assertThat(flyway(null).migrate().migrationsExecuted).isPositive();
        assertThat(filas()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT nombre FROM contribuyentes WHERE id=?",String.class,ID)).isEqualTo("Persona de prueba");
        assertThat(jdbc.queryForObject("SELECT documento FROM contribuyentes WHERE id=?",String.class,ID)).isEqualTo("00123456");
        assertThat(jdbc.queryForObject("SELECT numero_padron FROM padrones WHERE id=?",String.class,ID)).isEqualTo("000007");
        assertThat(numero("SELECT contribuyente_id FROM padrones WHERE id=?",ID)).isEqualTo(ID);
        assertThat(numero("SELECT padron_id FROM deuda WHERE id=?",ID)).isEqualTo(ID);
        assertThat(jdbc.queryForObject("SELECT importe FROM deuda WHERE id=?",java.math.BigDecimal.class,ID)).isEqualByComparingTo("123.45");
        assertThat(jdbc.queryForObject("SELECT estado FROM deuda WHERE id=?",String.class,ID)).isEqualTo("EN_GESTION");
        assertThat(numero("SELECT deuda_id FROM gestiones WHERE id=?",ID)).isEqualTo(ID);
        assertThat(jdbc.queryForObject("SELECT observacion FROM gestiones WHERE id=?",String.class,ID)).isEqualTo("Historial anterior");
        assertThat(jdbc.queryForObject("SELECT password FROM usuarios WHERE id=?",String.class,ID)).isEqualTo("hash-ficticio-conservado");
        assertThat(jdbc.queryForObject("SELECT activo FROM usuarios WHERE id=?",Boolean.class,ID)).isFalse();
        assertThat(jdbc.queryForList("SELECT rol FROM usuario_roles WHERE usuario_id=?",String.class,ID)).contains("NOTIFICADOR");
        assertThat(columna("usuarios","rol")).isFalse();assertThat(columna("contribuyentes","cm")).isFalse();
        assertThat(jdbc.queryForObject("SELECT origen FROM padrones WHERE id=?",String.class,ID)).isEqualTo("INTENDENCIA");
        assertThat(jdbc.queryForObject("SELECT origen FROM tributos WHERE id=?",String.class,ID)).isEqualTo("INTENDENCIA");
        assertThat(jdbc.queryForObject("SELECT origen FROM contactos WHERE id=?",String.class,ID)).isEqualTo("F".repeat(100));
        assertThat(flyway(null).validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
    }
    @Test void rolesSimplesSeMigranSinCambiarUsuarioYAdmitenCombinaciones() {
        flyway("202610121930").migrate();
        for(String rol:List.of("ADMINISTRADOR","NOTIFICADOR","ANALISTA"))
            jdbc.update("INSERT INTO usuarios(username,password,rol,activo) VALUES (?,?,?,true)","prueba-"+rol,"hash-ficticio",rol);
        var antes=jdbc.queryForList("SELECT id,username,password,activo FROM usuarios ORDER BY id");
        flyway(null).migrate();
        assertThat(jdbc.queryForList("SELECT id,username,password,activo FROM usuarios ORDER BY id")).isEqualTo(antes);
        assertThat(numero("SELECT count(*) FROM usuario_roles")).isEqualTo(3);
        for(String rol:List.of("ADMINISTRADOR","NOTIFICADOR","ANALISTA"))
            assertThat(numero("SELECT count(*) FROM usuario_roles r JOIN usuarios u ON u.id=r.usuario_id WHERE u.username=? AND r.rol=?","prueba-"+rol,rol)).isEqualTo(1);
        jdbc.update("INSERT INTO usuario_roles SELECT id,'NOTIFICADOR' FROM usuarios WHERE username='prueba-ADMINISTRADOR'");
        jdbc.update("INSERT INTO usuario_roles SELECT id,'ANALISTA' FROM usuarios WHERE username='prueba-ADMINISTRADOR'");
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
        assertThat(numero("SELECT count(*) FROM usuario_roles r JOIN usuarios u ON u.id=r.usuario_id WHERE u.username='prueba-ADMINISTRADOR'")).isEqualTo(3);
    }
    @Test void secuenciasEnBloquesNoReutilizanIdsAnteriores() {
        flyway("202610071300").migrate();semilla();flyway(null).migrate();
        for(String tabla:List.of("contribuyentes","padrones","deuda","tributos","contactos")) {
            long max=numero("SELECT max(id) FROM "+tabla);
            long valor=numero("SELECT nextval('"+tabla+"_id_seq')");
            assertThat(valor-999).as("primer ID del bloque de "+tabla).isGreaterThan(max);
            assertThat(numero("SELECT nextval('"+tabla+"_id_seq')")-valor).isEqualTo(1000);
        }
    }
    @Test void rangosAntiguosPorTributoQuedanComoHistorialSinAsignarlosAUnPadTipo() {
        flyway("202610131200").migrate();semilla();
        jdbc.update("INSERT INTO configuraciones_segmento(id,segmento_mora_id,tributo_id,dias_desde,dias_hasta,activa,fecha_creacion) VALUES (5001,1,5001,0,30,true,now())");
        flyway(null).migrate();
        assertThat(jdbc.queryForObject("SELECT activa FROM configuraciones_segmento WHERE id=5001",Boolean.class)).isFalse();
        assertThat(numero("SELECT tributo_id FROM configuraciones_segmento WHERE id=5001")).isEqualTo(ID);
        assertThat(jdbc.queryForObject("SELECT tipo_padron FROM configuraciones_segmento WHERE id=5001",String.class)).isNull();
        assertThat(numero("SELECT count(*) FROM configuraciones_segmento WHERE activa AND tributo_id IS NULL AND tipo_padron IS NULL")).isEqualTo(3);
    }
    @Test void migracionesRecientesConservanPeriodoYContactosYPermitenNuevosLimites() {
        flyway("202611121000").migrate();semilla();
        jdbc.update("UPDATE configuracion_sincronizacion SET anio_minimo=2015 WHERE id=1");
        flyway(null).migrate();
        assertThat(numero("SELECT anio_minimo FROM configuracion_sincronizacion WHERE id=1")).isEqualTo(2015);
        jdbc.update("UPDATE configuracion_sincronizacion SET anio_minimo=NULL WHERE id=1");
        assertThat(jdbc.queryForObject("SELECT anio_minimo FROM configuracion_sincronizacion WHERE id=1",Integer.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT origen FROM contactos WHERE id=?",String.class,ID)).isEqualTo("F".repeat(100));
        jdbc.update("UPDATE contactos SET origen=? WHERE id=?","X".repeat(255),ID);
        assertThat(jdbc.queryForObject("SELECT origen FROM contactos WHERE id=?",String.class,ID)).hasSize(255);
        assertThat(jdbc.queryForObject("SELECT en_periodo_gestion FROM deuda WHERE id=?",Boolean.class,ID)).isTrue();
    }
    @Test void checksumAlteradoBloqueaLaMigracionSinRepararloAutomaticamente() {
        var f=flyway(null);f.migrate();semilla();var antes=filas();
        jdbc.update("UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='1'");
        assertThat(f.validateWithResult().validationSuccessful).isFalse();
        assertThatThrownBy(f::migrate).isInstanceOf(FlywayException.class);
        assertThat(filas()).isEqualTo(antes);
    }
    @Test void falloDeMigracionRevierteDdlYConservaDatosParaReintentar() {
        flyway("202610121930").migrate();semilla();
        jdbc.update("UPDATE usuarios SET rol='ROL_INVALIDO' WHERE id=?",ID);
        assertThatThrownBy(()->flyway(null).migrate()).isInstanceOf(FlywayException.class);
        assertThat(columna("usuarios","rol")).isTrue();assertThat(tabla("usuario_roles")).isFalse();
        assertThat(numero("SELECT count(*) FROM gestiones WHERE id=?",ID)).isEqualTo(1);
        jdbc.update("UPDATE usuarios SET rol='NOTIFICADOR' WHERE id=?",ID);
        flyway(null).migrate();assertThat(flyway(null).validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForList("SELECT rol FROM usuario_roles WHERE usuario_id=?",String.class,ID)).containsExactly("NOTIFICADOR");
    }
    Map<String,Long> filas() {
        var resultado=new LinkedHashMap<String,Long>();
        for(String tabla:List.of("contribuyentes","padrones","deuda","tributos","deuda_tributo","contactos","usuarios","gestiones","capturas_cartera","captura_cuentas"))
            resultado.put(tabla,tabla(tabla)?numero("SELECT count(*) FROM "+tabla):0L);
        return resultado;
    }
    void semilla() {
        jdbc.update("INSERT INTO contribuyentes(id,nombre,documento) VALUES (?,?,?)",ID,"Persona de prueba","00123456");
        jdbc.update("INSERT INTO padrones(id,cm,numero_padron,tipo_padron,localidad,contribuyente_id) VALUES (?,?,?,?,?,?)",ID,"CM-TEST","000007","CIU","Prueba",ID);
        jdbc.update("INSERT INTO deuda(id,padron_id,importe,deuda_desde,estado,fecha_creacion,fecha_actualizacion) VALUES (?,?,123.45,'2026-01-01','EN_GESTION',now(),now())",ID,ID);
        jdbc.update("INSERT INTO tributos(id,codigo,descripcion) VALUES (?,?,?)",ID,"TEST-MIG","Descripción conservada");
        jdbc.update("INSERT INTO deuda_tributo(deuda_id,tributo_id) VALUES (?,?)",ID,ID);
        jdbc.update("INSERT INTO contactos(id,tipo_contacto,valor,es_de_contribuyente,origen,contribuyente_id) VALUES (?,'EMAIL','prueba@example.test',true,?,?)",ID,"F".repeat(100),ID);
        if(columna("usuarios","rol"))
            jdbc.update("INSERT INTO usuarios(id,username,password,rol,activo) VALUES (?,'prueba-migracion','hash-ficticio-conservado','NOTIFICADOR',false)",ID);
        else {
            jdbc.update("INSERT INTO usuarios(id,username,password,activo) VALUES (?,'prueba-migracion','hash-ficticio-conservado',false)",ID);
            jdbc.update("INSERT INTO usuario_roles(usuario_id,rol) VALUES (?,'NOTIFICADOR')",ID);
        }
        jdbc.update("INSERT INTO gestiones(id,fecha,resultado,observacion,deuda_id,accion_id) SELECT ?,now(),'PENDIENTE','Historial anterior',?,min(id) FROM acciones",ID,ID);
        if(tabla("capturas_cartera")) {
            jdbc.update("INSERT INTO capturas_cartera(id,nombre,fecha,usuario) VALUES (?,'Captura anterior',now(),'prueba')",ID);
            jdbc.update("INSERT INTO captura_cuentas(captura_id,cm,origen,numero_padron,nombre,documento,importe,estado) VALUES (?,'CM-TEST','INTENDENCIA','000007','Persona de prueba','00123456',123.45,'PENDIENTE')",ID);
        }
    }
}
