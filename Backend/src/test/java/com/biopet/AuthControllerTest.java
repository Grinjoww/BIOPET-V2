package com.biopet;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.UsuarioRepository;
import com.biopet.security.TokenBlacklistService;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
