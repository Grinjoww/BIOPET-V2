package com.biopet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersTest {

    private static final String ENDPOINT_PROTEGIDO = "/api/usuarios/me";
    private static final String ENDPOINT_LOGIN = "/api/auth/login";
    private static final String ORIGEN_PERMITIDO = "http://localhost:4200";
    private static final String ORIGEN_NO_PERMITIDO = "https://evil.example";

    @Autowired MockMvc mockMvc;

    @Test
    void respuestaProtegidaIncluyeCabecerasDeSeguridad() throws Exception {
        MvcResult result = mockMvc.perform(get(ENDPOINT_PROTEGIDO).secure(true))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andReturn();

        MockHttpServletResponse response = result.getResponse();

        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));

        String csp = response.getHeader("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("default-src 'self'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(csp.contains("object-src 'none'"));
        assertFalse(csp.contains("frame-ancestors 'self'"));
        assertFalse(csp.contains("unsafe-inline"));
        assertFalse(csp.contains("unsafe-eval"));

        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));

        String hsts = response.getHeader("Strict-Transport-Security");
        assertNotNull(hsts);
        assertTrue(hsts.contains("max-age=31536000"));
        assertTrue(hsts.contains("includeSubDomains"));
        assertTrue(hsts.contains("preload"));

        assertEquals(401, response.getStatus());
    }

    @Test
    void peticionHttpNoIncluyeHsts() throws Exception {
        MvcResult result = mockMvc.perform(get(ENDPOINT_PROTEGIDO))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MockHttpServletResponse response = result.getResponse();

        assertNull(response.getHeader("Strict-Transport-Security"));

        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertNotNull(response.getHeader("Content-Security-Policy"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));

        assertEquals(401, response.getStatus());
    }

    @Test
    void preflightCorsDesdeOrigenPermitidoAceptaCredenciales() throws Exception {
        MvcResult result = mockMvc.perform(options(ENDPOINT_LOGIN)
                        .header("Origin", ORIGEN_PERMITIDO)
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn();

        MockHttpServletResponse response = result.getResponse();

        assertEquals(ORIGEN_PERMITIDO, response.getHeader("Access-Control-Allow-Origin"));
        assertNotEquals("*", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));

        String metodosPermitidos = response.getHeader("Access-Control-Allow-Methods");
        assertNotNull(metodosPermitidos);
        assertTrue(metodosPermitidos.contains("POST"));
    }

    @Test
    void preflightCorsDesdeOrigenNoPermitidoEsRechazado() throws Exception {
        MvcResult result = mockMvc.perform(options(ENDPOINT_LOGIN)
                        .header("Origin", ORIGEN_NO_PERMITIDO)
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn();

        MockHttpServletResponse response = result.getResponse();

        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        assertNotEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void respuestasPublicasTambienIncluyenCabecerasBasicas() throws Exception {
        MvcResult result = mockMvc.perform(post(ENDPOINT_LOGIN)
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nadie@biopet.com","password":"cualquiera"}
                                """))
                .andReturn();

        MockHttpServletResponse response = result.getResponse();

        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));

        String csp = response.getHeader("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("default-src 'self'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(csp.contains("object-src 'none'"));

        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));

        String hsts = response.getHeader("Strict-Transport-Security");
        assertNotNull(hsts);
        assertTrue(hsts.contains("max-age=31536000"));
    }
}
