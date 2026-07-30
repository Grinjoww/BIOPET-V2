package com.biopet.security;

import com.biopet.exception.RateLimitExcedidoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiterService {

    private static final String CLAVE_DESCONOCIDA = "desconocida";

    private final int maxAttempts;
    private final Duration window;
    private final Duration blockDuration;
    private final Clock clock;

    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();

    @Autowired
    public LoginRateLimiterService(
            @Value("${security.rate-limit.login.max-attempts:6}") int maxAttempts,
            @Value("${security.rate-limit.login.window:PT15M}") Duration window,
            @Value("${security.rate-limit.login.block-duration:PT15M}") Duration blockDuration
    ) {
        this(maxAttempts, window, blockDuration, Clock.systemUTC());
    }

    LoginRateLimiterService(int maxAttempts, Duration window, Duration blockDuration, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.blockDuration = blockDuration;
        this.clock = clock;
    }

    public void verificarPermitido(String ip) {
        String clave = normalizar(ip);
        Estado actual = estados.compute(clave, (key, estado) -> limpiarSiExpiro(estado));
        if (actual != null && actual.bloqueadaHasta() != null) {
            throw new RateLimitExcedidoException(segundosRestantes(actual.bloqueadaHasta()));
        }
    }

    public void registrarFallo(String ip) {
        String clave = normalizar(ip);
        Estado[] resultado = new Estado[1];

        estados.compute(clave, (key, estadoPrevio) -> {
            Estado vigente = limpiarSiExpiro(estadoPrevio);
            Instant ahora = clock.instant();

            int nuevosFallos = (vigente == null) ? 1 : vigente.fallos() + 1;
            Instant inicioVentana = (vigente == null) ? ahora : vigente.inicioVentana();
            Instant bloqueadaHasta = (nuevosFallos >= maxAttempts) ? ahora.plus(blockDuration) : null;

            Estado nuevo = new Estado(nuevosFallos, inicioVentana, bloqueadaHasta);
            resultado[0] = nuevo;
            return nuevo;
        });

        if (resultado[0].bloqueadaHasta() != null) {
            throw new RateLimitExcedidoException(segundosRestantes(resultado[0].bloqueadaHasta()));
        }
    }

    public void reiniciar(String ip) {
        estados.remove(normalizar(ip));
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

    private String normalizar(String ip) {
        if (ip == null || ip.isBlank()) {
            return CLAVE_DESCONOCIDA;
        }
        return ip.trim();
    }

    private record Estado(int fallos, Instant inicioVentana, Instant bloqueadaHasta) {
    }
}
