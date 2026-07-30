package com.biopet.security;

import com.biopet.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;
    private final long refreshExpirationMs;
    private final String issuer;
    private final String audience;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-ms}") long expirationMs,
            @Value("${security.jwt.refresh-expiration-ms}") long refreshExpirationMs,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience
    ) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET debe tener al menos 256 bits (32 bytes)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String generateAccessToken(Usuario usuario) {
        return buildToken(usuario, expirationMs, "access");
    }

    public String generateRefreshToken(Usuario usuario) {
        return buildToken(usuario, refreshExpirationMs, "refresh");
    }

    private String buildToken(Usuario usuario, long ttlMs, String tipo) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(ttlMs);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(usuario.getId()))
                .audience().add(audience).and()
                .claim("email", usuario.getEmail())
                .claim("rol", usuario.getRol().name())
                .claim("typ", tipo)
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(exp))
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractClaims(token).get("email", String.class);
    }

    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    public Instant extractExpiration(String token) {
        return extractClaims(token).getExpiration().toInstant();
    }

    public boolean isAccessToken(String token) {
        return "access".equals(extractClaims(token).get("typ", String.class));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(extractClaims(token).get("typ", String.class));
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
