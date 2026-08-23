package com.biopet.security;

import com.biopet.exception.RateLimitExcedidoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * H-1: rate limit propio de {@code POST /api/auth/registro} (público, sin
 * autenticación, ejecuta BCrypt en cada intento). Usa el mismo algoritmo que
 * {@link LoginRateLimiterService} (ver {@link RateLimiterCore}) pero con su
 * propio bucket en memoria y su propia política -no comparte contador con
 * login: un atacante agotando el bucket de registro no bloquea el login de
 * otros usuarios, ni viceversa.
 *
 * <p>A diferencia del login (donde solo los intentos FALLIDOS cuentan, y un
 * login exitoso reinicia el contador), aquí toda solicitud de registro
 * cuenta -éxito o fallo (p.ej. email duplicado)- porque cada una ya pagó el
 * costo de BCrypt antes de que este límite pueda evaluarla; lo que se quiere
 * acotar es el volumen de solicitudes por IP, no solo los fallos.
 */
@Component
public class RegistroRateLimiterService {

    private final RateLimiterCore core;

    @Autowired
    public RegistroRateLimiterService(
            @Value("${security.rate-limit.registro.max-attempts:10}") int maxAttempts,
            @Value("${security.rate-limit.registro.window:PT15M}") Duration window,
            @Value("${security.rate-limit.registro.block-duration:PT15M}") Duration blockDuration
    ) {
        this(maxAttempts, window, blockDuration, Clock.systemUTC());
    }

    RegistroRateLimiterService(int maxAttempts, Duration window, Duration blockDuration, Clock clock) {
        this.core = new RateLimiterCore(maxAttempts, window, blockDuration, clock);
    }

    /**
     * Verifica que la IP no esté ya bloqueada y registra esta solicitud de
     * registro como un intento más del bucket. Debe llamarse una única vez
     * por solicitud, antes de procesarla.
     */
    public void verificarPermitidoYRegistrarIntento(String ip) {
        Long segundosBloqueado = core.segundosBloqueoActual(ip);
        if (segundosBloqueado != null) {
            throw new RateLimitExcedidoException(segundosBloqueado, RateLimitExcedidoException.Recurso.REGISTRO);
        }
        Long segundosTrasIntento = core.registrarIntento(ip);
        if (segundosTrasIntento != null) {
            throw new RateLimitExcedidoException(segundosTrasIntento, RateLimitExcedidoException.Recurso.REGISTRO);
        }
    }
}
