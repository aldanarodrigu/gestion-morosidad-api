package com.intendencia.gestion_morosidad_api.modules.auth;

import com.intendencia.gestion_morosidad_api.shared.exception.LoginBloqueadoException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
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

@Service
public class LimitadorLogin {
    private record Intentos(int fallos, boolean bloqueada, OffsetDateTime venceEn) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int maxFallos;
    private final Duration ventana;
    private final Duration bloqueo;

    public LimitadorLogin(JdbcTemplate jdbc, Clock clock,
            @Value("${auth.login.max-fallos}") int maxFallos,
            @Value("${auth.login.ventana-segundos}") int ventanaSegundos,
            @Value("${auth.login.bloqueo-segundos}") int bloqueoSegundos) {
        Assert.isTrue(maxFallos > 0 && ventanaSegundos > 0 && bloqueoSegundos > 0,
                "El límite, la ventana y la duración del bloqueo de login deben ser positivos.");
        this.jdbc = jdbc;
        this.clock = clock;
        this.maxFallos = maxFallos;
        this.ventana = Duration.ofSeconds(ventanaSegundos);
        this.bloqueo = Duration.ofSeconds(bloqueoSegundos);
    }

    // El fallo debe confirmar el contador. Un error interno sigue revirtiendo la transacción.
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = AuthenticationException.class)
    public <T> T autenticar(String nombre, Supplier<T> validarCredenciales) {
        String cuenta = nombre.strip().toLowerCase(Locale.ROOT);
        // Serializa solo las validaciones de la misma cuenta, incluso entre instancias de la API.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 481003))",
                (rs, fila) -> Boolean.TRUE, cuenta);

        OffsetDateTime ahora = ahora();
        Intentos intentos = jdbc.query("""
                SELECT fallos, bloqueada, vence_en FROM intentos_login WHERE cuenta = ?
                """, (rs, fila) -> new Intentos(rs.getInt("fallos"), rs.getBoolean("bloqueada"),
                rs.getObject("vence_en", OffsetDateTime.class)), cuenta).stream().findFirst().orElse(null);
        if (intentos != null && !intentos.venceEn().isAfter(ahora)) intentos = null;
        if (intentos != null && intentos.bloqueada()) {
            long segundos = Math.ceilDiv(Duration.between(ahora, intentos.venceEn()).toMillis(), 1000L);
            throw new LoginBloqueadoException(Math.max(1L, segundos));
        }

        T respuesta;
        try {
            respuesta = validarCredenciales.get();
        } catch (BadCredentialsException | AccountStatusException ex) {
            registrarFallo(cuenta, intentos);
            throw ex;
        }
        jdbc.update("DELETE FROM intentos_login WHERE cuenta = ?", cuenta);
        return respuesta;
    }

    private void registrarFallo(String cuenta, Intentos anteriores) {
        OffsetDateTime ahora = ahora();
        int fallos = anteriores == null ? 1 : anteriores.fallos() + 1;
        boolean bloqueada = fallos >= maxFallos;
        OffsetDateTime vence = bloqueada ? ahora.plus(bloqueo)
                : anteriores == null ? ahora.plus(ventana) : anteriores.venceEn();
        jdbc.update("""
                INSERT INTO intentos_login (cuenta, fallos, bloqueada, vence_en)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (cuenta) DO UPDATE SET
                    fallos = EXCLUDED.fallos, bloqueada = EXCLUDED.bloqueada, vence_en = EXCLUDED.vence_en
                """, cuenta, fallos, bloqueada, vence);
    }

    @Scheduled(initialDelay = 900_000, fixedDelay = 900_000)
    public void limpiarVencidos() {
        jdbc.update("DELETE FROM intentos_login WHERE vence_en <= ?", ahora());
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
