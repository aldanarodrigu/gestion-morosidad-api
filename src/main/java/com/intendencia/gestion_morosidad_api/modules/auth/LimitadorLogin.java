package com.intendencia.gestion_morosidad_api.modules.auth;

import com.intendencia.gestion_morosidad_api.shared.exception.LoginBloqueadoException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Limita los fallos de inicio de sesión con tres contadores en {@code intentos_login}:
 * <ul>
 *   <li><b>cuenta + IP</b> ({@code max-fallos}): el límite habitual. Bloquea solo a esa dirección, así
 *       nadie puede dejar sin acceso a una cuenta conocida (por ejemplo {@code admin}) desde otra IP.</li>
 *   <li><b>IP</b> ({@code max-fallos-ip}): fallos de una dirección contra cualquier cuenta. Frena probar
 *       una misma clave contra muchas cuentas.</li>
 *   <li><b>cuenta</b> ({@code max-fallos-cuenta}): tope alto para ataques repartidos entre muchas IPs.</li>
 * </ul>
 * Cada clave lleva un prefijo distinto para que un nombre de usuario no pueda coincidir con otro tipo.
 */
@Service
public class LimitadorLogin {
    private record Contador(String clave, int maximo) {}
    private record Intentos(int fallos, boolean bloqueada, OffsetDateTime venceEn) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int maxFallos;
    private final int maxFallosIp;
    private final int maxFallosCuenta;
    private final Duration ventana;
    private final Duration bloqueo;

    public LimitadorLogin(JdbcTemplate jdbc, Clock clock,
            @Value("${auth.login.max-fallos}") int maxFallos,
            @Value("${auth.login.max-fallos-ip}") int maxFallosIp,
            @Value("${auth.login.max-fallos-cuenta}") int maxFallosCuenta,
            @Value("${auth.login.ventana-segundos}") int ventanaSegundos,
            @Value("${auth.login.bloqueo-segundos}") int bloqueoSegundos) {
        Assert.isTrue(maxFallos > 0 && maxFallosIp > 0 && maxFallosCuenta > 0
                        && ventanaSegundos > 0 && bloqueoSegundos > 0,
                "Los límites, la ventana y la duración del bloqueo de login deben ser positivos.");
        this.jdbc = jdbc;
        this.clock = clock;
        this.maxFallos = maxFallos;
        this.maxFallosIp = maxFallosIp;
        this.maxFallosCuenta = maxFallosCuenta;
        this.ventana = Duration.ofSeconds(ventanaSegundos);
        this.bloqueo = Duration.ofSeconds(bloqueoSegundos);
    }

    public static String claveCuentaIp(String cuenta, String ip) { return "cuenta-ip:" + ip + "|" + normalizar(cuenta); }
    public static String claveIp(String ip) { return "ip:" + ip; }
    public static String claveCuenta(String cuenta) { return "cuenta:" + normalizar(cuenta); }

    // El fallo debe confirmar los contadores. Un error interno sigue revirtiendo la transacción.
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = AuthenticationException.class)
    public <T> T autenticar(String nombre, String ip, Supplier<T> validarCredenciales) {
        String origen = ip == null || ip.isBlank() ? "desconocida" : ip.strip();
        Contador cuentaIp = new Contador(claveCuentaIp(nombre, origen), maxFallos);
        Contador porIp = new Contador(claveIp(origen), maxFallosIp);
        Contador porCuenta = new Contador(claveCuenta(nombre), maxFallosCuenta);
        List<Contador> contadores = List.of(cuentaIp, porIp, porCuenta);

        // Serializa las validaciones que comparten un contador, incluso entre instancias de la API.
        // Se toman en orden fijo para que dos logins simultáneos no se esperen mutuamente.
        contadores.stream().map(Contador::clave).sorted().forEach(clave ->
                jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 481003))",
                        (rs, fila) -> Boolean.TRUE, clave));

        OffsetDateTime ahora = ahora();
        Map<Contador, Intentos> vigentes = new HashMap<>();
        long esperar = 0;
        for (Contador contador : contadores) {
            Intentos intentos = leer(contador.clave(), ahora);
            if (intentos == null) continue;
            vigentes.put(contador, intentos);
            if (intentos.bloqueada()) {
                long segundos = Math.ceilDiv(Duration.between(ahora, intentos.venceEn()).toMillis(), 1000L);
                esperar = Math.max(esperar, Math.max(1L, segundos));
            }
        }
        if (esperar > 0) throw new LoginBloqueadoException(esperar);

        T respuesta;
        try {
            respuesta = validarCredenciales.get();
        } catch (BadCredentialsException | AccountStatusException ex) {
            contadores.forEach(contador -> registrarFallo(contador, vigentes.get(contador)));
            throw ex;
        }
        // El contador de la IP no se limpia: con una cuenta válida propia se podría reiniciar entre intentos.
        jdbc.update("DELETE FROM intentos_login WHERE cuenta IN (?, ?)", cuentaIp.clave(), porCuenta.clave());
        return respuesta;
    }

    private Intentos leer(String clave, OffsetDateTime ahora) {
        Intentos intentos = jdbc.query("""
                SELECT fallos, bloqueada, vence_en FROM intentos_login WHERE cuenta = ?
                """, (rs, fila) -> new Intentos(rs.getInt("fallos"), rs.getBoolean("bloqueada"),
                rs.getObject("vence_en", OffsetDateTime.class)), clave).stream().findFirst().orElse(null);
        return intentos != null && intentos.venceEn().isAfter(ahora) ? intentos : null;
    }

    private void registrarFallo(Contador contador, Intentos anteriores) {
        OffsetDateTime ahora = ahora();
        int fallos = anteriores == null ? 1 : anteriores.fallos() + 1;
        boolean bloqueada = fallos >= contador.maximo();
        OffsetDateTime vence = bloqueada ? ahora.plus(bloqueo)
                : anteriores == null ? ahora.plus(ventana) : anteriores.venceEn();
        jdbc.update("""
                INSERT INTO intentos_login (cuenta, fallos, bloqueada, vence_en)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (cuenta) DO UPDATE SET
                    fallos = EXCLUDED.fallos, bloqueada = EXCLUDED.bloqueada, vence_en = EXCLUDED.vence_en
                """, contador.clave(), fallos, bloqueada, vence);
    }

    @Scheduled(initialDelay = 900_000, fixedDelay = 900_000)
    public void limpiarVencidos() {
        jdbc.update("DELETE FROM intentos_login WHERE vence_en <= ?", ahora());
    }

    private static String normalizar(String cuenta) {
        return cuenta.strip().toLowerCase(Locale.ROOT);
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
