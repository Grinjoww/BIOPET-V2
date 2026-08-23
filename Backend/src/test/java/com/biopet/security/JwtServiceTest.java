package com.biopet.security;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "9c8f9a7d6e5b4c3a2d1f0e9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c";
    private static final String ISSUER = "biopet-test-api";
    private static final String AUDIENCE = "biopet-test-frontend";
    private static final long EXPIRATION_MS = 3_600_000L;
    private static final long REFRESH_EXPIRATION_MS = 604_800_000L;

    private JwtService jwtService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_MS, REFRESH_EXPIRATION_MS, ISSUER, AUDIENCE);
        usuario = Usuario.builder()
                .id(1L)
                .nombre("Jaime Mariscal")
                .email("jaime@biopet.com")
                .passwordHash("hash-irrelevante-para-esta-prueba")
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build();
    }

    @Test
    void accessTokenContieneLosSieteClaims() {
        String token = jwtService.generateAccessToken(usuario);
        Claims claims = jwtService.extractClaims(token);

        assertEquals(ISSUER, claims.getIssuer());
        assertEquals(String.valueOf(usuario.getId()), claims.getSubject());
        assertTrue(claims.getAudience().contains(AUDIENCE));
        assertNotNull(claims.getExpiration());
        assertNotNull(claims.getNotBefore());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getId());
        assertFalse(claims.getId().isBlank());
        assertEquals("access", claims.get("typ", String.class));
        assertEquals(usuario.getEmail(), claims.get("email", String.class));
        assertEquals(usuario.getRol().name(), claims.get("rol", String.class));
    }

    @Test
    void refreshTokenContieneLosSieteClaims() {
        String token = jwtService.generateRefreshToken(usuario);
        Claims claims = jwtService.extractClaims(token);

        assertEquals(ISSUER, claims.getIssuer());
        assertEquals(String.valueOf(usuario.getId()), claims.getSubject());
        assertTrue(claims.getAudience().contains(AUDIENCE));
        assertNotNull(claims.getExpiration());
        assertNotNull(claims.getNotBefore());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getId());
        assertFalse(claims.getId().isBlank());
        assertEquals("refresh", claims.get("typ", String.class));
    }

    @Test
    void nbfEsIgualAIat() {
        String token = jwtService.generateAccessToken(usuario);
        Claims claims = jwtService.extractClaims(token);

        assertEquals(claims.getIssuedAt(), claims.getNotBefore());
    }

    @Test
    void jtiEsUnicoEntreTokens() {
        String token1 = jwtService.generateAccessToken(usuario);
        String token2 = jwtService.generateAccessToken(usuario);

        String jti1 = jwtService.extractJti(token1);
        String jti2 = jwtService.extractJti(token2);

        assertNotNull(jti1);
        assertFalse(jti1.isBlank());
        assertNotNull(jti2);
        assertFalse(jti2.isBlank());
        assertNotEquals(jti1, jti2);
    }

    @Test
    void issuerIncorrectoEsRechazado() {
        String token = construirTokenFirmadoCon("otro-issuer", AUDIENCE);

        assertThrows(IncorrectClaimException.class, () -> jwtService.extractClaims(token));
    }

    @Test
    void audienceIncorrectaEsRechazada() {
        String token = construirTokenFirmadoCon(ISSUER, "otra-audiencia");

        assertThrows(IncorrectClaimException.class, () -> jwtService.extractClaims(token));
    }

    @Test
    void tokenExpiradoEsRechazado() {
        JwtService servicioConTokensExpirados = new JwtService(SECRET, -1000L, -1000L, ISSUER, AUDIENCE);
        String token = servicioConTokensExpirados.generateAccessToken(usuario);

        assertThrows(ExpiredJwtException.class, () -> jwtService.extractClaims(token));
    }

    @Test
    void accessYRefreshSeDistinguenCorrectamente() {
        String access = jwtService.generateAccessToken(usuario);
        String refresh = jwtService.generateRefreshToken(usuario);

        assertTrue(jwtService.isAccessToken(access));
        assertFalse(jwtService.isRefreshToken(access));
        assertTrue(jwtService.isRefreshToken(refresh));
        assertFalse(jwtService.isAccessToken(refresh));
    }

    @Test
    void secretoMenorA256BitsLanzaIllegalArgumentException() {
        String secretoCorto = "demasiado-corto"; // 15 bytes UTF-8 < 32

        assertThrows(IllegalArgumentException.class, () ->
                new JwtService(secretoCorto, EXPIRATION_MS, REFRESH_EXPIRATION_MS, ISSUER, AUDIENCE));
    }

    @Test
    void secretoDeExactamente32BytesNoLanzaExcepcion() {
        String secreto32Bytes = "a".repeat(32);

        assertNotNull(new JwtService(secreto32Bytes, EXPIRATION_MS, REFRESH_EXPIRATION_MS, ISSUER, AUDIENCE));
    }

    private String construirTokenFirmadoCon(String issuer, String audience) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(usuario.getId()))
                .audience().add(audience).and()
                .claim("email", usuario.getEmail())
                .claim("rol", usuario.getRol().name())
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
