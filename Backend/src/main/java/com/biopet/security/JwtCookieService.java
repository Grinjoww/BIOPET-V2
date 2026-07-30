package com.biopet.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class JwtCookieService {

    private static final String ACCESS_PATH = "/";
    private static final String REFRESH_PATH = "/api/auth";

    private final String accessCookieName;
    private final String refreshCookieName;
    private final boolean secure;
    private final String sameSite;
    private final Duration accessMaxAge;
    private final Duration refreshMaxAge;

    public JwtCookieService(
            @Value("${security.cookie.access-name}") String accessCookieName,
            @Value("${security.cookie.refresh-name}") String refreshCookieName,
            @Value("${security.cookie.secure}") boolean secure,
            @Value("${security.cookie.same-site}") String sameSite,
            @Value("${security.jwt.expiration-ms}") long expirationMs,
            @Value("${security.jwt.refresh-expiration-ms}") long refreshExpirationMs
    ) {
        this.accessCookieName = accessCookieName;
        this.refreshCookieName = refreshCookieName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.accessMaxAge = Duration.ofMillis(expirationMs);
        this.refreshMaxAge = Duration.ofMillis(refreshExpirationMs);
    }

    public void addAccessCookie(HttpServletResponse response, String token) {
        writeCookie(response, accessCookieName, token, ACCESS_PATH, accessMaxAge);
    }

    public void addRefreshCookie(HttpServletResponse response, String token) {
        writeCookie(response, refreshCookieName, token, REFRESH_PATH, refreshMaxAge);
    }

    public void clearAccessCookie(HttpServletResponse response) {
        writeCookie(response, accessCookieName, "", ACCESS_PATH, Duration.ZERO);
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        writeCookie(response, refreshCookieName, "", REFRESH_PATH, Duration.ZERO);
    }

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return readCookie(request, accessCookieName);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return readCookie(request, refreshCookieName);
    }

    private void writeCookie(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        if (response.isCommitted()) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value == null || value.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
