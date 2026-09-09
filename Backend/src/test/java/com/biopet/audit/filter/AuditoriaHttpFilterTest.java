package com.biopet.audit.filter;

import com.biopet.audit.service.AuditoriaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Prueba AuditoriaHttpFilter directamente con MockHttpServletRequest/
 * Response -sin @SpringBootTest-, para poder fijar SecurityContextHolder a
 * mano y cubrir cada combinacion metodo/ruta/autenticacion sin el costo de
 * arrancar el contexto completo. La cobertura end-to-end real (una peticion
 * autenticada de verdad generando una fila en auditoria_evento) vive en
 * AuditoriaControllerTest.
 */
class AuditoriaHttpFilterTest {

    private AuditoriaService auditoriaService;
    private AuditoriaHttpFilter filter;

    @BeforeEach
    void setUp() {
        auditoriaService = mock(AuditoriaService.class);
        filter = new AuditoriaHttpFilter(auditoriaService);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void postAutenticadoContraApiEsAuditado() throws Exception {
        autenticarComo("admin@biopet.ec");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mascotas");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        filter.doFilter(request, response, (req, res) -> {});

        verify(auditoriaService).registrar(eq("admin@biopet.ec"), eq("POST"), eq("mascotas"),
                eq("/api/mascotas"), eq("POST"), eq("SUCCESS"), eq(201));
    }

    @Test
    void statusDeErrorSeRegistraComoFailure() throws Exception {
        autenticarComo("admin@biopet.ec");
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/usuarios/5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(403);

        filter.doFilter(request, response, (req, res) -> {});

        verify(auditoriaService).registrar(eq("admin@biopet.ec"), eq("DELETE"), eq("usuarios"),
                eq("/api/usuarios/5"), eq("DELETE"), eq("FAILURE"), eq(403));
    }

    @Test
    void getNuncaSeAudita() throws Exception {
        autenticarComo("admin@biopet.ec");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mascotas");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, (req, res) -> {});

        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void peticionAnonimaNuncaSeAudita() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mascotas");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(401);

        filter.doFilter(request, response, (req, res) -> {});

        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sinAutenticacionEnElContextoNuncaSeAudita() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mascotas");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(401);

        filter.doFilter(request, response, (req, res) -> {});

        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rutaDeAutenticacionQuedaExcluidaAunqueLlegaseAutenticada() throws Exception {
        autenticarComo("admin@biopet.ec");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, (req, res) -> {});

        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rutaDeRespaldosQuedaExcluidaPorTenerSusPropiosEventosNombrados() throws Exception {
        autenticarComo("admin@biopet.ec");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/respaldos/ejecutar");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(202);

        filter.doFilter(request, response, (req, res) -> {});

        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rutaFueraDeApiQuedaExcluida() throws Exception {
        autenticarComo("admin@biopet.ec");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, (req, res) -> {});

        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void moduloSeExtraeDelPrimerSegmentoDeLaRuta() throws Exception {
        autenticarComo("admin@biopet.ec");
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/facturacion/conceptos/7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, (req, res) -> {});

        verify(auditoriaService).registrar(eq("admin@biopet.ec"), eq("PUT"), eq("facturacion"),
                eq("/api/facturacion/conceptos/7"), eq("PUT"), eq("SUCCESS"), eq(200));
    }

    @Test
    void unFalloAlRegistrarNuncaPropagaLaExcepcion() throws Exception {
        autenticarComo("admin@biopet.ec");
        org.mockito.Mockito.doThrow(new RuntimeException("fallo simulado"))
                .when(auditoriaService).registrar(any(), any(), any(), any(), any(), any(), any());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mascotas");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        // No debe lanzar: el filtro nunca puede tumbar una respuesta ya generada.
        filter.doFilter(request, response, (req, res) -> {});
    }

    private void autenticarComo(String email) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, authorities));
    }
}
