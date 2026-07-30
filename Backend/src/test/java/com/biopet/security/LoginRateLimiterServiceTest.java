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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimiterServiceTest {

    private static final int MAX_ATTEMPTS = 6;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private MutableClock reloj;
    private LoginRateLimiterService limiter;

    @BeforeEach
    void setUp() {
        reloj = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        limiter = new LoginRateLimiterService(MAX_ATTEMPTS, WINDOW, BLOCK_DURATION, reloj);
    }

    @Test
    void cincoFallosNoBloquean() {
        String ip = "203.0.113.10";

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.registrarFallo(ip));
        }

        assertDoesNotThrow(() -> limiter.verificarPermitido(ip));
    }

    @Test
    void sextoFalloBloqueaYLanzaExcepcion() {
        String ip = "203.0.113.11";

        for (int i = 0; i < 5; i++) {
            limiter.registrarFallo(ip);
        }

        RateLimitExcedidoException ex = assertThrows(RateLimitExcedidoException.class,
                () -> limiter.registrarFallo(ip));

        assertEquals(BLOCK_DURATION.getSeconds(), ex.getSegundosRestantes());
    }

    @Test
    void ipBloqueadaSigueRechazadaConTiempoRestante() {
        String ip = "203.0.113.12";

        for (int i = 0; i < 5; i++) {
            limiter.registrarFallo(ip);
        }
        assertThrows(RateLimitExcedidoException.class, () -> limiter.registrarFallo(ip));

        reloj.avanzar(Duration.ofMinutes(5));

        RateLimitExcedidoException ex = assertThrows(RateLimitExcedidoException.class,
                () -> limiter.verificarPermitido(ip));

        assertEquals(Duration.ofMinutes(10).getSeconds(), ex.getSegundosRestantes());
        assertTrue(ex.getSegundosRestantes() > 0);
    }

    @Test
    void ipsDiferentesMantienenContadoresSeparados() {
        String ipBloqueada = "203.0.113.20";
        String ipLibre = "203.0.113.21";

        for (int i = 0; i < 5; i++) {
            limiter.registrarFallo(ipBloqueada);
        }
        assertThrows(RateLimitExcedidoException.class, () -> limiter.registrarFallo(ipBloqueada));

        assertDoesNotThrow(() -> limiter.verificarPermitido(ipLibre));
        assertDoesNotThrow(() -> limiter.registrarFallo(ipLibre));
    }

    @Test
    void reiniciarEliminaFallosYBloqueo() {
        String ip = "203.0.113.30";

        for (int i = 0; i < 5; i++) {
            limiter.registrarFallo(ip);
        }
        assertThrows(RateLimitExcedidoException.class, () -> limiter.registrarFallo(ip));

        limiter.reiniciar(ip);

        assertDoesNotThrow(() -> limiter.verificarPermitido(ip));
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.registrarFallo(ip));
        }
        assertDoesNotThrow(() -> limiter.verificarPermitido(ip));
    }

    @Test
    void ventanaExpiradaReiniciaElContador() {
        String ip = "203.0.113.40";

        for (int i = 0; i < 3; i++) {
            limiter.registrarFallo(ip);
        }

        reloj.avanzar(WINDOW.plusMinutes(1));

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.registrarFallo(ip));
        }
        assertThrows(RateLimitExcedidoException.class, () -> limiter.registrarFallo(ip));
    }

    @Test
    void bloqueoExpiradoPermiteNuevoIntento() {
        String ip = "203.0.113.50";

        for (int i = 0; i < 5; i++) {
            limiter.registrarFallo(ip);
        }
        assertThrows(RateLimitExcedidoException.class, () -> limiter.registrarFallo(ip));

        reloj.avanzar(BLOCK_DURATION.plusSeconds(1));

        assertDoesNotThrow(() -> limiter.verificarPermitido(ip));
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.registrarFallo(ip));
        }
        assertThrows(RateLimitExcedidoException.class, () -> limiter.registrarFallo(ip));
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
