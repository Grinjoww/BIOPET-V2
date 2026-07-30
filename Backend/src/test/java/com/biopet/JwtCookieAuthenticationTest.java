package com.biopet;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.security.AuthenticationAuditService;
import com.biopet.security.JwtService;
import com.biopet.security.TokenBlacklistService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtCookieAuthenticationTest {

    private static final String JWT_SECRET_DE_PRUEBA =
            "9c8f9a7d6e5b4c3a2d1f0e9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c";
    private static final String JWT_ISSUER_DE_PRUEBA = "biopet-test-api";
    private static final String JWT_AUDIENCE_DE_PRUEBA = "biopet-test-frontend";

    @Autowired MockMvc mockMvc;

    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean AuthenticationAuditService authenticationAuditService;

    @BeforeEach
    void setUp() {
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void accessCookieValidaPermiteAcceso() throws Exception {
        registrarUsuario("cookie.valida@biopet.com", "ClaveSegura123*");
        MvcResult loginResult = iniciarSesion("cookie.valida@biopet.com", "ClaveSegura123*");
        String accessToken = extractCookieValue(loginResult, "access_token");

        mockMvc.perform(get("/api/usuarios/me")
                        .cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cookie.valida@biopet.com"));

        verify(authenticationAuditService, never()).tokenRevocado(any(), any());
    }

    @Test
    void accessCookieInvalidaDevuelveProblemDetail401() throws Exception {
        mockMvc.perform(get("/api/usuarios/me")
                        .cookie(new Cookie("access_token", "token-invalido")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/usuarios/me"));

        verify(authenticationAuditService, never()).tokenRevocado(any(), any());
    }

    @Test
    void refreshTokenNoSirveComoAccessCookie() throws Exception {
        registrarUsuario("cookie.refresh@biopet.com", "ClaveSegura123*");
        MvcResult loginResult = iniciarSesion("cookie.refresh@biopet.com", "ClaveSegura123*");
        String refreshToken = extractCookieValue(loginResult, "refresh_token");

        mockMvc.perform(get("/api/usuarios/me")
                        .cookie(new Cookie("access_token", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/usuarios/me"));

        verify(authenticationAuditService, never()).tokenRevocado(any(), any());
    }

    @Test
    void refreshTokenComoBearerTampocoInvocaAuditoriaDeRevocacion() throws Exception {
        registrarUsuario("cookie.refresh.bearer@biopet.com", "ClaveSegura123*");
        MvcResult loginResult = iniciarSesion("cookie.refresh.bearer@biopet.com", "ClaveSegura123*");
        String refreshToken = extractCookieValue(loginResult, "refresh_token");

        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.status").value(401));

        verify(authenticationAuditService, never()).tokenRevocado(any(), any());
    }

    @Test
    void accessCookieRevocadaDevuelve401() throws Exception {
        registrarUsuario("cookie.revocada@biopet.com", "ClaveSegura123*");
        MvcResult loginResult = iniciarSesion("cookie.revocada@biopet.com", "ClaveSegura123*");
        String accessToken = extractCookieValue(loginResult, "access_token");

        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(true);

        mockMvc.perform(get("/api/usuarios/me")
                        .cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/usuarios/me"));

        verify(authenticationAuditService, times(1)).tokenRevocado(anyString(), eq("cookie.revocada@biopet.com"));
    }

    @Test
    void tokenExpiradoNoInvocaAuditoriaDeRevocacion() throws Exception {
        Usuario usuarioDePrueba = Usuario.builder()
                .id(999L)
                .nombre("Usuario Expirado")
                .email("cookie.expirada@biopet.com")
                .passwordHash("hash-irrelevante-para-esta-prueba")
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build();
        JwtService servicioConTokenExpirado = new JwtService(
                JWT_SECRET_DE_PRUEBA, -1000L, -1000L, JWT_ISSUER_DE_PRUEBA, JWT_AUDIENCE_DE_PRUEBA);
        String accessTokenExpirado = servicioConTokenExpirado.generateAccessToken(usuarioDePrueba);

        mockMvc.perform(get("/api/usuarios/me")
                        .cookie(new Cookie("access_token", accessTokenExpirado)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.status").value(401));

        verify(authenticationAuditService, never()).tokenRevocado(any(), any());
    }

    @Test
    void bearerSigueFuncionandoCuandoNoExisteCookie() throws Exception {
        registrarUsuario("cookie.bearer@biopet.com", "ClaveSegura123*");
        MvcResult loginResult = iniciarSesion("cookie.bearer@biopet.com", "ClaveSegura123*");
        String accessToken = extractCookieValue(loginResult, "access_token");

        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cookie.bearer@biopet.com"));
    }

    @Test
    void cookieTienePrioridadSobreBearer() throws Exception {
        registrarUsuario("cookie.prioridad@biopet.com", "ClaveSegura123*");
        MvcResult loginResult = iniciarSesion("cookie.prioridad@biopet.com", "ClaveSegura123*");
        String accessTokenValido = extractCookieValue(loginResult, "access_token");

        mockMvc.perform(get("/api/usuarios/me")
                        .cookie(new Cookie("access_token", "token-invalido"))
                        .header("Authorization", "Bearer " + accessTokenValido))
                .andExpect(status().isUnauthorized());
    }

    private void registrarUsuario(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Usuario Prueba","email":"%s","password":"%s","rol":"ROLE_DUENO"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated());
    }

    private MvcResult iniciarSesion(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String extractCookieValue(MvcResult result, String cookieName) {
        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String header = setCookieHeaders.stream()
                .filter(value -> value.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró la cookie '" + cookieName + "' en las cabeceras Set-Cookie"));
        int separatorIndex = header.indexOf(';');
        String pair = separatorIndex >= 0 ? header.substring(0, separatorIndex) : header;
        return pair.substring(cookieName.length() + 1);
    }
}
