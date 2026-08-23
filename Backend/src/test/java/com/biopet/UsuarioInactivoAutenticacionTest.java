package com.biopet;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.UsuarioRepository;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A4: un usuario con access token criptograficamente valido (no expirado,
 * no revocado) pero cuya cuenta pasa a activo=false DESPUES de emitido el
 * token no debe conservar acceso. GET /api/usuarios/me se usa como ruta
 * protegida de control porque no exige ningun rol especifico
 * (@AuthenticationPrincipal + autenticacion basta), evitando la complejidad
 * de un rol ROLE_ADMIN innecesaria para este caso.
 * <p>
 * Cada test usa su propio email unico (mismo patron que
 * JwtCookieAuthenticationTest, sin @Transactional): no se toca ningun dato
 * QA existente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsuarioInactivoAutenticacionTest {

    private static final String JWT_SECRET_DE_PRUEBA =
            "9c8f9a7d6e5b4c3a2d1f0e9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c";
    private static final String JWT_ISSUER_DE_PRUEBA = "biopet-test-api";
    private static final String JWT_AUDIENCE_DE_PRUEBA = "biopet-test-frontend";

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;

    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean AuthenticationAuditService authenticationAuditService;

    @BeforeEach
    void setUp() {
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void tokenValidoUsuarioActivo_permiteAcceso() throws Exception {
        String email = "a4.activo@biopet.com";
        registrarUsuario(email, "ClaveSegura123*");
        MvcResult login = iniciarSesion(email, "ClaveSegura123*");
        String accessToken = extractCookieValue(login, "access_token");

        mockMvc.perform(get("/api/usuarios/me")
                        .cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void tokenValidoUsuarioLuegoInactivo_devuelve401() throws Exception {
        String email = "a4.luego.inactivo@biopet.com";
        registrarUsuario(email, "ClaveSegura123*");
        MvcResult login = iniciarSesion(email, "ClaveSegura123*");
        String accessToken = extractCookieValue(login, "access_token");

        // Control: el mismo token todavia funciona mientras la cuenta esta activa.
        mockMvc.perform(get("/api/usuarios/me")
                        .cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk());

        desactivarUsuario(email);

        // Mismo token, no expirado, no revocado por jti -- solo la cuenta cambio.
        mockMvc.perform(get("/api/usuarios/me")
                        .cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/usuarios/me"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.nombre").doesNotExist());
    }

    @Test
    void tokenDeUsuarioInexistente_devuelve401() throws Exception {
        // Usuario nunca persistido: mismo patron que
        // JwtCookieAuthenticationTest.tokenExpiradoNoInvocaAuditoriaDeRevocacion
        // (JwtService real, sin pasar por /api/auth/login).
        Usuario fantasma = Usuario.builder()
                .id(987654321L)
                .nombre("Usuario Fantasma")
                .email("a4.no-existe@biopet.com")
                .passwordHash("hash-irrelevante-para-esta-prueba")
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build();
        JwtService jwtService = new JwtService(
                JWT_SECRET_DE_PRUEBA, 3_600_000L, 604_800_000L, JWT_ISSUER_DE_PRUEBA, JWT_AUDIENCE_DE_PRUEBA);
        String token = jwtService.generateAccessToken(fantasma);

        mockMvc.perform(get("/api/usuarios/me")
                        .cookie(new Cookie("access_token", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void usuarioInactivoNoPuedeRefrescarSuSesion() throws Exception {
        // AUDITADO por separado (ver informe): AuthService.refresh() tambien
        // usa findByEmailAndActivoTrue, asi que una cuenta inactiva NUNCA
        // recibe un access token nuevo -eso es correcto en cuanto a
        // seguridad-, pero la excepcion (RecursoNoEncontradoException) se
        // lanza DENTRO del controller (no en un filtro), y
        // GlobalExceptionHandler ya la traduce a 404, no a 500. Este test fija
        // ese comportamiento actual; el informe reporta la inconsistencia de
        // status (404 vs 401) como hallazgo separado, sin corregirla aqui.
        String email = "a4.refresh.inactivo@biopet.com";
        registrarUsuario(email, "ClaveSegura123*");
        MvcResult login = iniciarSesion(email, "ClaveSegura123*");
        String refreshToken = extractCookieValue(login, "refresh_token");

        desactivarUsuario(email);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNotFound())
                // Sin charset: GlobalExceptionHandler usa
                // ResponseEntity.contentType(APPLICATION_PROBLEM_JSON) sin
                // setear charset explicito (a diferencia de
                // ProblemAuthenticationEntryPoint, que si lo hace) -
                // inconsistencia menor preexistente, no parte de A4.
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    private void registrarUsuario(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Usuario Prueba A4","email":"%s","password":"%s","rol":"ROLE_DUENO"}
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

    private void desactivarUsuario(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
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
