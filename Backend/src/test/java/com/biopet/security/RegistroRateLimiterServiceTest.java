package com.biopet.security;

import com.biopet.exception.RateLimitExcedidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * H-1: rate limit propio del registro público. A diferencia de
 * {@link LoginRateLimiterServiceTest}, aquí TODA llamada cuenta como intento
 * (no solo los fallos) -no existe un "éxito" que reinicie el contador, porque
 * el propio abuso que se quiere frenar es la alta masiva de cuentas exitosas
 * desde una misma IP.
 */
class RegistroRateLimiterServiceTest {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private MutableClock reloj;
    private RegistroRateLimiterService limiter;

    @BeforeEach
    void setUp() {
        reloj = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        limiter = new RegistroRateLimiterService(MAX_ATTEMPTS, WINDOW, BLOCK_DURATION, reloj);
    }

    @Test
    void nueveIntentosNoBloquean() {
        String ip = "203.0.113.10";

        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            assertDoesNotThrow(() -> limiter.verificarPermitidoYRegistrarIntento(ip));
        }
    }

    @Test
    void decimoIntentoBloqueaYLanza429DeRegistro() {
        String ip = "203.0.113.11";

        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            limiter.verificarPermitidoYRegistrarIntento(ip);
        }

        RateLimitExcedidoException ex = assertThrows(RateLimitExcedidoException.class,
                () -> limiter.verificarPermitidoYRegistrarIntento(ip));

        assertEquals(RateLimitExcedidoException.Recurso.REGISTRO, ex.getRecurso());
        assertEquals(BLOCK_DURATION.getSeconds(), ex.getSegundosRestantes());
    }

    @Test
    void ipBloqueadaSigueRechazadaSinContarComoNuevoIntento() {
        String ip = "203.0.113.12";

        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            limiter.verificarPermitidoYRegistrarIntento(ip);
        }
        assertThrows(RateLimitExcedidoException.class, () -> limiter.verificarPermitidoYRegistrarIntento(ip));

        reloj.avanzar(Duration.ofMinutes(5));

        RateLimitExcedidoException ex = assertThrows(RateLimitExcedidoException.class,
                () -> limiter.verificarPermitidoYRegistrarIntento(ip));

        assertEquals(Duration.ofMinutes(10).getSeconds(), ex.getSegundosRestantes());
    }

    @Test
    void ipsDiferentesMantienenBucketsSeparados() {
        String ipBloqueada = "203.0.113.20";
        String ipLibre = "203.0.113.21";

        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            limiter.verificarPermitidoYRegistrarIntento(ipBloqueada);
        }
        assertThrows(RateLimitExcedidoException.class, () -> limiter.verificarPermitidoYRegistrarIntento(ipBloqueada));

        assertDoesNotThrow(() -> limiter.verificarPermitidoYRegistrarIntento(ipLibre));
    }

    @Test
    void registroYLoginNoComparteBucketParaLaMismaIp() {
        String ip = "203.0.113.30";
        LoginRateLimiterService loginLimiter = new LoginRateLimiterService(6, WINDOW, BLOCK_DURATION, reloj);

        // Agota el bucket de LOGIN para esta IP.
        for (int i = 0; i < 5; i++) {
            loginLimiter.registrarFallo(ip);
        }
        assertThrows(RateLimitExcedidoException.class, () -> loginLimiter.registrarFallo(ip));

        // El bucket de REGISTRO para la misma IP sigue intacto.
        assertDoesNotThrow(() -> limiter.verificarPermitidoYRegistrarIntento(ip));
    }

    @Test
    void loginYRegistroNoComparteBucketParaLaMismaIp() {
        String ip = "203.0.113.31";

        // Agota el bucket de REGISTRO para esta IP.
        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            limiter.verificarPermitidoYRegistrarIntento(ip);
        }
        assertThrows(RateLimitExcedidoException.class, () -> limiter.verificarPermitidoYRegistrarIntento(ip));

        // El bucket de LOGIN para la misma IP sigue intacto.
        LoginRateLimiterService loginLimiter = new LoginRateLimiterService(6, WINDOW, BLOCK_DURATION, reloj);
        assertDoesNotThrow(() -> loginLimiter.verificarPermitido(ip));
        assertDoesNotThrow(() -> loginLimiter.registrarFallo(ip));
    }

    @Test
    void bloqueoExpiradoPermiteNuevosIntentos() {
        String ip = "203.0.113.40";

        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            limiter.verificarPermitidoYRegistrarIntento(ip);
        }
        assertThrows(RateLimitExcedidoException.class, () -> limiter.verificarPermitidoYRegistrarIntento(ip));

        reloj.avanzar(BLOCK_DURATION.plusSeconds(1));

        assertDoesNotThrow(() -> limiter.verificarPermitidoYRegistrarIntento(ip));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void avanzar(Duration duracion) {
            instant = instant.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
