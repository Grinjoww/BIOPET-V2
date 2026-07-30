package com.biopet.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtCookieServiceTest {

    private static final String ACCESS_NAME = "access_token";
    private static final String REFRESH_NAME = "refresh_token";
    private static final long EXPIRATION_MS = 3_600_000L;
    private static final long REFRESH_EXPIRATION_MS = 604_800_000L;

    private JwtCookieService jwtCookieService;

    @BeforeEach
    void setUp() {
        jwtCookieService = new JwtCookieService(
                ACCESS_NAME,
                REFRESH_NAME,
                true,
                "Strict",
                EXPIRATION_MS,
                REFRESH_EXPIRATION_MS
        );
    }

    @Test
    void accessCookieTieneAtributosSeguros() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtCookieService.addAccessCookie(response, "token-de-prueba");

        List<String> atributos = atributosDe(response, ACCESS_NAME);
        assertEquals(ACCESS_NAME + "=token-de-prueba", atributos.get(0));
        assertTrue(atributos.contains("HttpOnly"));
        assertTrue(atributos.contains("Secure"));
        assertTrue(atributos.contains("SameSite=Strict"));
        assertTrue(atributos.contains("Path=/"));
        assertTrue(atributos.contains("Max-Age=3600"));
    }

    @Test
    void refreshCookieTieneAtributosSeguros() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtCookieService.addRefreshCookie(response, "refresh-de-prueba");

        List<String> atributos = atributosDe(response, REFRESH_NAME);
        assertEquals(REFRESH_NAME + "=refresh-de-prueba", atributos.get(0));
        assertTrue(atributos.contains("HttpOnly"));
        assertTrue(atributos.contains("Secure"));
        assertTrue(atributos.contains("SameSite=Strict"));
        assertTrue(atributos.contains("Path=/api/auth"));
        assertTrue(atributos.contains("Max-Age=604800"));
    }

    @Test
    void cookiesSeAgreganComoCabecerasSeparadas() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtCookieService.addAccessCookie(response, "access-valor");
        jwtCookieService.addRefreshCookie(response, "refresh-valor");

        List<String> setCookieHeaders = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, setCookieHeaders.size());
        assertTrue(setCookieHeaders.get(0).startsWith(ACCESS_NAME + "="));
        assertTrue(setCookieHeaders.get(1).startsWith(REFRESH_NAME + "="));
    }

    @Test
    void clearAccessCookieUsaMismoNombreYPath() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtCookieService.clearAccessCookie(response);

        List<String> atributos = atributosDe(response, ACCESS_NAME);
        assertEquals(ACCESS_NAME + "=", atributos.get(0));
        assertTrue(atributos.contains("Path=/"));
        assertTrue(atributos.contains("Max-Age=0"));
        assertTrue(atributos.contains("HttpOnly"));
        assertTrue(atributos.contains("Secure"));
        assertTrue(atributos.contains("SameSite=Strict"));
    }

    @Test
    void clearRefreshCookieUsaMismoNombreYPath() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtCookieService.clearRefreshCookie(response);

        List<String> atributos = atributosDe(response, REFRESH_NAME);
        assertEquals(REFRESH_NAME + "=", atributos.get(0));
        assertTrue(atributos.contains("Path=/api/auth"));
        assertTrue(atributos.contains("Max-Age=0"));
        assertTrue(atributos.contains("HttpOnly"));
        assertTrue(atributos.contains("Secure"));
        assertTrue(atributos.contains("SameSite=Strict"));
    }

    @Test
    void readAccessTokenEncuentraCookieCorrecta() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("otra-cookie", "otro-valor"),
                new Cookie(ACCESS_NAME, "token-leido")
        );

        Optional<String> token = jwtCookieService.readAccessToken(request);

        assertTrue(token.isPresent());
        assertEquals("token-leido", token.get());
    }

    @Test
    void readRefreshTokenEncuentraCookieCorrecta() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("otra-cookie", "otro-valor"),
                new Cookie(REFRESH_NAME, "refresh-leido")
        );

        Optional<String> token = jwtCookieService.readRefreshToken(request);

        assertTrue(token.isPresent());
        assertEquals("refresh-leido", token.get());
    }

    @Test
    void lecturaSinCookiesDevuelveOptionalVacio() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertTrue(jwtCookieService.readAccessToken(request).isEmpty());
        assertTrue(jwtCookieService.readRefreshToken(request).isEmpty());
    }

    @Test
    void lecturaConValorVacioDevuelveOptionalVacio() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(ACCESS_NAME, ""));

        Optional<String> token = jwtCookieService.readAccessToken(request);

        assertFalse(token.isPresent());
    }

    private List<String> atributosDe(MockHttpServletResponse response, String cookieName) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(cookieName + "="))
                .findFirst()
                .map(header -> Arrays.stream(header.split(";"))
                        .map(String::trim)
                        .collect(Collectors.toList()))
                .orElseThrow();
    }
}
