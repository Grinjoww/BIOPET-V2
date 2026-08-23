package com.biopet.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * H-1: cobertura aislada (sin Spring, sin BCrypt) de la respuesta 429 para
 * ambos recursos protegidos por rate limit -confirma que login y registro
 * comparten el mismo formato ProblemDetail/Retry-After pero con un detail
 * distinto por recurso.
 */
class GlobalExceptionHandlerRateLimitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void loginExcedidoDevuelve429ConDetailDeLogin() {
        HttpServletRequest request = requestPara("/api/auth/login");

        ResponseEntity<ProblemDetail> response = handler.demasiadosIntentos(
                new RateLimitExcedidoException(42), request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        assertEquals("42", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));

        ProblemDetail body = response.getBody();
        assertEquals(ProblemType.RATE_LIMITED.uri(), body.getType());
        assertEquals("Demasiados intentos", body.getTitle());
        assertEquals("Se ha excedido el número máximo de intentos fallidos de inicio de sesión. Intente nuevamente más tarde.",
                body.getDetail());
        assertEquals("/api/auth/login", body.getInstance().toString());
    }

    @Test
    void registroExcedidoDevuelve429ConDetailDeRegistro() {
        HttpServletRequest request = requestPara("/api/auth/registro");

        ResponseEntity<ProblemDetail> response = handler.demasiadosIntentos(
                new RateLimitExcedidoException(30, RateLimitExcedidoException.Recurso.REGISTRO), request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("30", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));

        ProblemDetail body = response.getBody();
        assertEquals(ProblemType.RATE_LIMITED.uri(), body.getType());
        assertEquals("Demasiados intentos", body.getTitle());
        assertEquals("Se ha excedido el número máximo de solicitudes de registro. Intente nuevamente más tarde.",
                body.getDetail());
        assertEquals("/api/auth/registro", body.getInstance().toString());
    }

    private HttpServletRequest requestPara(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }
}
