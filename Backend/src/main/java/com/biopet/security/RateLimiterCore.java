package com.biopet.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Algoritmo de rate limiting (ventana deslizante + bloqueo temporal) por
 * clave (normalmente una IP), compartido entre {@link LoginRateLimiterService}
 * y {@link RegistroRateLimiterService} -H-1: el registro público necesita la
 * misma protección que el login, pero con su propio bucket (prefijo/política
 * independientes), sin duplicar el algoritmo.
 *
 * <p>No es un {@code @Component}: cada servicio dueño de un bucket lo
 * instancia con su propia configuración (max intentos, ventana, duración de
 * bloqueo) y su propio mapa en memoria -no comparten estado entre buckets.
 * No requiere Redis: el limitador de login original tampoco lo usaba, y esta
 * extracción preserva esa arquitectura.
 */
final class RateLimiterCore {

    private static final String CLAVE_DESCONOCIDA = "desconocida";

    private final int maxAttempts;
    private final Duration window;
    private final Duration blockDuration;
    private final Clock clock;

    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();

    RateLimiterCore(int maxAttempts, Duration window, Duration blockDuration, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.blockDuration = blockDuration;
        this.clock = clock;
    }

    /**
     * @return segundos restantes de bloqueo si la clave ya está bloqueada en
     * este momento, o {@code null} si está permitida (no registra ningún
     * intento nuevo).
     */
    Long segundosBloqueoActual(String clave) {
        String normalizada = normalizar(clave);
        Estado actual = estados.compute(normalizada, (key, estado) -> limpiarSiExpiro(estado));
        return (actual != null && actual.bloqueadaHasta() != null) ? segundosRestantes(actual.bloqueadaHasta()) : null;
    }

    /**
     * Registra un intento (fallo de login, o solicitud de registro) para la
     * clave dada.
     *
     * @return segundos restantes de bloqueo si este intento activa (o
     * mantiene) el bloqueo, o {@code null} si la clave sigue permitida.
     */
    Long registrarIntento(String clave) {
        String normalizada = normalizar(clave);
        Estado[] resultado = new Estado[1];

        estados.compute(normalizada, (key, estadoPrevio) -> {
            Estado vigente = limpiarSiExpiro(estadoPrevio);
            Instant ahora = clock.instant();

            int nuevosIntentos = (vigente == null) ? 1 : vigente.intentos() + 1;
            Instant inicioVentana = (vigente == null) ? ahora : vigente.inicioVentana();
            Instant bloqueadaHasta = (nuevosIntentos >= maxAttempts) ? ahora.plus(blockDuration) : null;

            Estado nuevo = new Estado(nuevosIntentos, inicioVentana, bloqueadaHasta);
            resultado[0] = nuevo;
            return nuevo;
        });

        return (resultado[0].bloqueadaHasta() != null) ? segundosRestantes(resultado[0].bloqueadaHasta()) : null;
    }

    void reiniciar(String clave) {
        estados.remove(normalizar(clave));
    }

    private Estado limpiarSiExpiro(Estado estado) {
        if (estado == null) {
            return null;
        }
        Instant ahora = clock.instant();
        if (estado.bloqueadaHasta() != null) {
            return ahora.isBefore(estado.bloqueadaHasta()) ? estado : null;
        }
        if (!ahora.isBefore(estado.inicioVentana().plus(window))) {
            return null;
        }
        return estado;
    }

    private long segundosRestantes(Instant bloqueadaHasta) {
        Duration restante = Duration.between(clock.instant(), bloqueadaHasta);
        if (restante.isNegative() || restante.isZero()) {
            return 1;
        }
        long segundos = (restante.toMillis() + 999) / 1000;
        return Math.max(segundos, 1);
    }

    private String normalizar(String clave) {
        if (clave == null || clave.isBlank()) {
            return CLAVE_DESCONOCIDA;
        }
        return clave.trim();
    }

    private record Estado(int intentos, Instant inicioVentana, Instant bloqueadaHasta) {
    }
}
