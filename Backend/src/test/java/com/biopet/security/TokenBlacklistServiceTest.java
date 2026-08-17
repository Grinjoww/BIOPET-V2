package com.biopet.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobertura de ramas de TokenBlacklistService: TTL negativo, TTL cero y TTL
 * positivo en revoke(), y ambos resultados booleanos de isRevoked().
 */
class TokenBlacklistServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private TokenBlacklistService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new TokenBlacklistService(redisTemplate);
    }

    @Test
    void ttlNegativoNoEscribeEnRedis() {
        Instant expiroHaceUnMinuto = Instant.now().minusSeconds(60);

        service.revoke("jti-expirado", expiroHaceUnMinuto);

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void ttlCeroNoEscribeEnRedis() {
        Instant ahora = Instant.now();

        service.revoke("jti-cero", ahora);

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void ttlPositivoEscribeEnRedisConTtlRestante() {
        Instant expiraEnUnaHora = Instant.now().plusSeconds(3600);

        service.revoke("jti-vigente", expiraEnUnaHora);

        verify(valueOperations, times(1)).set(
                org.mockito.ArgumentMatchers.eq("jwt:blacklist:jti-vigente"),
                org.mockito.ArgumentMatchers.eq("revoked"),
                any(Duration.class));
    }

    @Test
    void isRevokedDevuelveTrueCuandoLaClaveExiste() {
        when(redisTemplate.hasKey("jwt:blacklist:jti-1")).thenReturn(Boolean.TRUE);

        assertTrue(service.isRevoked("jti-1"));
    }

    @Test
    void isRevokedDevuelveFalseCuandoLaClaveNoExiste() {
        when(redisTemplate.hasKey("jwt:blacklist:jti-2")).thenReturn(Boolean.FALSE);

        assertFalse(service.isRevoked("jti-2"));
    }

    @Test
    void isRevokedDevuelveFalseCuandoRedisDevuelveNull() {
        when(redisTemplate.hasKey("jwt:blacklist:jti-3")).thenReturn(null);

        assertFalse(service.isRevoked("jti-3"));
    }
}
