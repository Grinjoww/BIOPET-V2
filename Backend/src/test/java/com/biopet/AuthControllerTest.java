package com.biopet;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.UsuarioRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class AuthControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @MockBean TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        Usuario usuario = Usuario.builder()
                .nombre("Jaime Mariscal")
                .email("jaime@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build();
        usuarioRepository.save(usuario);
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void loginExitoso() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, setCookieHeaders.size());

        String accessCookieHeader = setCookieHeaders.stream()
                .filter(header -> header.startsWith("access_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se emitió la cookie access_token"));
        String refreshCookieHeader = setCookieHeaders.stream()
                .filter(header -> header.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se emitió la cookie refresh_token"));

        assertTrue(accessCookieHeader.contains("HttpOnly"));
        assertTrue(accessCookieHeader.contains("Secure"));
        assertTrue(accessCookieHeader.contains("SameSite=Strict"));
        assertTrue(accessCookieHeader.contains("Path=/"));

        assertTrue(refreshCookieHeader.contains("HttpOnly"));
        assertTrue(refreshCookieHeader.contains("Secure"));
        assertTrue(refreshCookieHeader.contains("SameSite=Strict"));
        assertTrue(refreshCookieHeader.contains("Path=/api/auth"));
    }

    @Test
    void loginClaveIncorrecta() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"incorrecta"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/auth/login"))
                .andReturn();

        assertTrue(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).isEmpty());
    }

    @Test
    void registroEmailDuplicado() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Jaime","email":"jaime@biopet.com","password":"OtraClave123*","rol":"ROLE_ADMIN"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:conflict"))
                .andExpect(jsonPath("$.title").value("Conflicto de datos"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/auth/registro"));
    }

    @Test
    void registroConCamposInvalidos() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","email":"no-es-un-email","password":"corta","rol":"ROLE_ADMIN"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:validation"))
                .andExpect(jsonPath("$.title").value("Error de validación"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").value("/api/auth/registro"))
                .andExpect(jsonPath("$.errors.nombre").isArray())
                .andExpect(jsonPath("$.errors.email").isArray())
                .andExpect(jsonPath("$.errors.password").isArray());
    }

    @Test
    void accesoSinToken() throws Exception {
        mockMvc.perform(get("/api/usuarios/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/usuarios/me"));
    }

    @Test
    void accesoConTokenInvalido() throws Exception {
        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/usuarios/me"));
    }

    @Test
    void accesoConTokenValido() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String token = extractCookieValue(loginResult, "access_token");

        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jaime@biopet.com"));
    }

    @Test
    void refreshCookieValidaEmiteNuevaAccessCookie() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = extractCookieValue(loginResult, "refresh_token");

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        List<String> setCookieHeaders = refreshResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(1, setCookieHeaders.size());

        String accessCookieHeader = setCookieHeaders.get(0);
        assertTrue(accessCookieHeader.startsWith("access_token="));
        assertTrue(accessCookieHeader.contains("HttpOnly"));
        assertTrue(accessCookieHeader.contains("Secure"));
        assertTrue(accessCookieHeader.contains("SameSite=Strict"));
        assertTrue(accessCookieHeader.contains("Path=/"));
    }

    @Test
    void refreshSinCookieDevuelve401ProblemDetail() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/auth/refresh"))
                .andReturn();

        assertTrue(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).isEmpty());
    }

    @Test
    void refreshCookieInvalidaDevuelve401ProblemDetail() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "token-invalido")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/auth/refresh"))
                .andReturn();

        assertTrue(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).isEmpty());
    }

    @Test
    void accessTokenNoSirveComoRefreshCookie() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractCookieValue(loginResult, "access_token");

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andReturn();

        assertTrue(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).isEmpty());
    }

    @Test
    void refreshCookieRevocadaDevuelve401() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = extractCookieValue(loginResult, "refresh_token");

        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(true);

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andReturn();

        assertTrue(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).isEmpty());
    }

    @Test
    void logoutConAmbasCookiesRevocaTokensYLasElimina() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractCookieValue(loginResult, "access_token");
        String refreshToken = extractCookieValue(loginResult, "refresh_token");
        String accessJti = jwtService.extractJti(accessToken);
        String refreshJti = jwtService.extractJti(refreshToken);

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("access_token", accessToken), new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> setCookieHeaders = logoutResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, setCookieHeaders.size());

        String accessCookieHeader = setCookieHeaders.stream()
                .filter(header -> header.startsWith("access_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se emitió la cookie de eliminación access_token"));
        String refreshCookieHeader = setCookieHeaders.stream()
                .filter(header -> header.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se emitió la cookie de eliminación refresh_token"));

        assertEquals("access_token=", primerAtributo(accessCookieHeader));
        assertTrue(accessCookieHeader.contains("Max-Age=0"));
        assertTrue(accessCookieHeader.contains("Path=/"));
        assertTrue(accessCookieHeader.contains("HttpOnly"));
        assertTrue(accessCookieHeader.contains("Secure"));
        assertTrue(accessCookieHeader.contains("SameSite=Strict"));

        assertEquals("refresh_token=", primerAtributo(refreshCookieHeader));
        assertTrue(refreshCookieHeader.contains("Max-Age=0"));
        assertTrue(refreshCookieHeader.contains("Path=/api/auth"));
        assertTrue(refreshCookieHeader.contains("HttpOnly"));
        assertTrue(refreshCookieHeader.contains("Secure"));
        assertTrue(refreshCookieHeader.contains("SameSite=Strict"));

        verify(tokenBlacklistService, times(1)).revoke(eq(accessJti), any(Instant.class));
        verify(tokenBlacklistService, times(1)).revoke(eq(refreshJti), any(Instant.class));
        verify(tokenBlacklistService, times(2)).revoke(anyString(), any(Instant.class));
    }

    @Test
    void logoutSinCookiesEsIdempotente() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, setCookieHeaders.size());
        assertTrue(setCookieHeaders.stream().anyMatch(header -> header.startsWith("access_token=")));
        assertTrue(setCookieHeaders.stream().anyMatch(header -> header.startsWith("refresh_token=")));

        verify(tokenBlacklistService, never()).revoke(anyString(), any(Instant.class));
    }

    @Test
    void logoutSoloConAccessCookieRevocaAccessYEliminaAmbas() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractCookieValue(loginResult, "access_token");

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, setCookieHeaders.size());
        assertTrue(setCookieHeaders.stream().anyMatch(header -> header.startsWith("access_token=")));
        assertTrue(setCookieHeaders.stream().anyMatch(header -> header.startsWith("refresh_token=")));

        verify(tokenBlacklistService, times(1)).revoke(anyString(), any(Instant.class));
    }

    @Test
    void logoutSoloConRefreshCookieRevocaRefreshYEliminaAmbas() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = extractCookieValue(loginResult, "refresh_token");

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, setCookieHeaders.size());
        assertTrue(setCookieHeaders.stream().anyMatch(header -> header.startsWith("access_token=")));
        assertTrue(setCookieHeaders.stream().anyMatch(header -> header.startsWith("refresh_token=")));

        verify(tokenBlacklistService, times(1)).revoke(anyString(), any(Instant.class));
    }

    @Test
    void logoutConCookiesInvalidasSigueSiendo204() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("access_token", "access-invalido"), new Cookie("refresh_token", "refresh-invalido")))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, setCookieHeaders.size());

        verify(tokenBlacklistService, never()).revoke(anyString(), any(Instant.class));
    }

    @Test
    void logoutProcesaTokenValidoAunqueElOtroSeaInvalido() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractCookieValue(loginResult, "access_token");

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("access_token", accessToken), new Cookie("refresh_token", "refresh-invalido")))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, setCookieHeaders.size());

        verify(tokenBlacklistService, times(1)).revoke(anyString(), any(Instant.class));
    }

    private String primerAtributo(String setCookieHeader) {
        int separatorIndex = setCookieHeader.indexOf(';');
        return separatorIndex >= 0 ? setCookieHeader.substring(0, separatorIndex) : setCookieHeader;
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
