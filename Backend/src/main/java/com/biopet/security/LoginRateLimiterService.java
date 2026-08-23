package com.biopet.security;

import com.biopet.exception.RateLimitExcedidoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class LoginRateLimiterService {

    private final RateLimiterCore core;

    @Autowired
    public LoginRateLimiterService(
            @Value("${security.rate-limit.login.max-attempts:6}") int maxAttempts,
            @Value("${security.rate-limit.login.window:PT15M}") Duration window,
            @Value("${security.rate-limit.login.block-duration:PT15M}") Duration blockDuration
    ) {
        this(maxAttempts, window, blockDuration, Clock.systemUTC());
    }

    LoginRateLimiterService(int maxAttempts, Duration window, Duration blockDuration, Clock clock) {
        this.core = new RateLimiterCore(maxAttempts, window, blockDuration, clock);
    }

    public void verificarPermitido(String ip) {
        Long segundosRestantes = core.segundosBloqueoActual(ip);
        if (segundosRestantes != null) {
            throw new RateLimitExcedidoException(segundosRestantes);
        }
    }

    public void registrarFallo(String ip) {
        Long segundosRestantes = core.registrarIntento(ip);
        if (segundosRestantes != null) {
            throw new RateLimitExcedidoException(segundosRestantes);
        }
    }

    public void reiniciar(String ip) {
        core.reiniciar(ip);
    }
}
