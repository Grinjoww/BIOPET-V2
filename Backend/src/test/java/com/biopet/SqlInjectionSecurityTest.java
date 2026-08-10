package com.biopet;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.CitaRepository;
import com.biopet.repository.ConsultaRepository;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import com.biopet.security.LoginRateLimiterService;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Evidencia OWASP A03 (Injection): demuestra que payloads de inyeccion SQL
 * nunca alcanzan la base de datos como SQL ejecutable. La proteccion real
 * es estructural (consultas parametrizadas de Spring Data + tipado fuerte
 * de {@code Long duenioId}), no un filtro de palabras: estos payloads son
 * simplemente valores de texto que no convierten a {@code Long}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SqlInjectionSecurityTest {

    private static final String EMAIL_VALIDO = "jaime@biopet.com";
    private static final String PASSWORD_VALIDO = "ClaveCorrecta123*";

    private static final String IP_LOGIN_INYECCION = "203.0.113.201";
    private static final String IP_LOGIN_POSTERIOR = "203.0.113.202";

    private static final String PAYLOAD_EMAIL_INYECCION =
            "admin@biopet.com' OR '1'='1";

    private static final String PAYLOAD_EMAIL_LITERAL_GUIA =
            "' OR '1'='1";

    private static final String PAYLOAD_OR =
            "1 OR 1=1";

    private static final String PAYLOAD_DROP =
            "1; DROP TABLE usuarios; --";

    private static final String PAYLOAD_UNION =
            "%' UNION SELECT NULL --";

    private static final String ENDPOINT_RESUMEN_ESPECIES =
            "/api/mascotas/resumen-especies";

    private static final List<String> PATRONES_FUGA_INTERNA = List.of(
            "org.hibernate",
            "org.postgresql",
            "sqlexception",
            "sqlgrammarexception",
            "stacktrace",
            "relation",
            "syntax error"
    );

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    MascotaRepository mascotaRepository;

    @Autowired
    CitaRepository citaRepository;

    @Autowired
    ConsultaRepository consultaRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    LoginRateLimiterService loginRateLimiterService;

    @MockBean
    TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        // Consultas y citas tienen FK hacia mascotas.
        // Primero se eliminan los registros hijos y después las mascotas.
        consultaRepository.deleteAll();
        citaRepository.deleteAll();
        mascotaRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario usuario = Usuario.builder()
                .nombre("Jaime Mariscal")
                .email(EMAIL_VALIDO)
                .passwordHash(passwordEncoder.encode(PASSWORD_VALIDO))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build();

        usuarioRepository.save(usuario);

        when(tokenBlacklistService.isRevoked(anyString()))
                .thenReturn(false);

        for (String ip : List.of(
                IP_LOGIN_INYECCION,
                IP_LOGIN_POSTERIOR
        )) {
            loginRateLimiterService.reiniciar(ip);
        }
    }

    @Test
    void loginConEmailDeInyeccionNoAutentica() throws Exception {
        MvcResult result = loginDesde(
                PAYLOAD_EMAIL_INYECCION,
                "ClaveCualquiera123*",
                IP_LOGIN_INYECCION
        )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.type")
                        .value("urn:biopet:error:validation"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errors.email").exists())
                .andReturn();

        List<String> cookies =
                result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);

        assertTrue(
                cookies.stream()
                        .noneMatch(c -> c.startsWith("access_token="))
        );

        assertTrue(
                cookies.stream()
                        .noneMatch(c -> c.startsWith("refresh_token="))
        );

        assertSinFugaDeInformacionInterna(
                result.getResponse().getContentAsString()
        );

        loginDesde(
                EMAIL_VALIDO,
                PASSWORD_VALIDO,
                IP_LOGIN_POSTERIOR
        )
                .andExpect(status().isOk());
    }

    @Test
    void loginConPayloadLiteralDeLaGuiaDevuelve422() throws Exception {
        MvcResult result = loginDesde(
                PAYLOAD_EMAIL_LITERAL_GUIA,
                "ClaveCualquiera123*",
                IP_LOGIN_INYECCION
        )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.type")
                        .value("urn:biopet:error:validation"))
                .andExpect(jsonPath("$.title")
                        .value("Error de validación"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance")
                        .value("/api/auth/login"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andReturn();

        String body =
                result.getResponse().getContentAsString();

        assertFalse(
                body.contains(PAYLOAD_EMAIL_LITERAL_GUIA),
                "El body no debe reflejar el payload completo enviado."
        );

        assertSinFugaDeInformacionInterna(body);
    }

    @Test
    void parametroDuenioIdConOrDevuelveProblemDetail400()
            throws Exception {

        String token = obtenerAccessTokenAdmin();

        MvcResult result = mockMvc.perform(
                        get(ENDPOINT_RESUMEN_ESPECIES)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .param(
                                        "duenioId",
                                        PAYLOAD_OR
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.type")
                        .value("urn:biopet:error:bad-request"))
                .andExpect(jsonPath("$.title")
                        .value("Parámetro inválido"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail")
                        .value(
                                "El parámetro 'duenioId' tiene un formato inválido."
                        ))
                .andExpect(jsonPath("$.instance")
                        .value(ENDPOINT_RESUMEN_ESPECIES))
                .andReturn();

        String body =
                result.getResponse().getContentAsString();

        assertFalse(
                body.contains(PAYLOAD_OR),
                "El body no debe reflejar el payload completo enviado."
        );

        assertSinFugaDeInformacionInterna(body);
    }

    @Test
    void parametroDuenioIdConDropDevuelveProblemDetail400()
            throws Exception {

        String token = obtenerAccessTokenAdmin();

        long usuariosAntes =
                usuarioRepository.count();

        long mascotasAntes =
                mascotaRepository.count();

        MvcResult result = mockMvc.perform(
                        get(ENDPOINT_RESUMEN_ESPECIES)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .param(
                                        "duenioId",
                                        PAYLOAD_DROP
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.type")
                        .value("urn:biopet:error:bad-request"))
                .andExpect(jsonPath("$.title")
                        .value("Parámetro inválido"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail")
                        .value(
                                "El parámetro 'duenioId' tiene un formato inválido."
                        ))
                .andExpect(jsonPath("$.instance")
                        .value(ENDPOINT_RESUMEN_ESPECIES))
                .andReturn();

        String body =
                result.getResponse().getContentAsString();

        assertFalse(
                body.contains(PAYLOAD_DROP),
                "El body no debe reflejar el payload completo enviado."
        );

        assertSinFugaDeInformacionInterna(body);

        assertEquals(
                usuariosAntes,
                usuarioRepository.count()
        );

        assertEquals(
                mascotasAntes,
                mascotaRepository.count()
        );
    }

    @Test
    void parametroDuenioIdConUnionDevuelveProblemDetail400()
            throws Exception {

        String token = obtenerAccessTokenAdmin();

        MvcResult result = mockMvc.perform(
                        get(ENDPOINT_RESUMEN_ESPECIES)
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .param(
                                        "duenioId",
                                        PAYLOAD_UNION
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.type")
                        .value("urn:biopet:error:bad-request"))
                .andExpect(jsonPath("$.title")
                        .value("Parámetro inválido"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail")
                        .value(
                                "El parámetro 'duenioId' tiene un formato inválido."
                        ))
                .andExpect(jsonPath("$.instance")
                        .value(ENDPOINT_RESUMEN_ESPECIES))
                .andReturn();

        String body =
                result.getResponse().getContentAsString();

        assertFalse(
                body.contains(PAYLOAD_UNION),
                "El body no debe reflejar el payload completo enviado."
        );

        assertSinFugaDeInformacionInterna(body);
    }

    @Test
    void consultasValidasSiguenFuncionando()
            throws Exception {

        String token = obtenerAccessTokenAdmin();

        mockMvc.perform(
                        get("/api/usuarios/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email")
                        .value(EMAIL_VALIDO));

        mockMvc.perform(
                        get("/api/mascotas")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());
    }

    @Test
    void respuestaNoFiltraInformacionDeBaseDeDatos()
            throws Exception {

        MvcResult loginInyeccion = loginDesde(
                PAYLOAD_EMAIL_INYECCION,
                "ClaveCualquiera123*",
                IP_LOGIN_INYECCION
        )
                .andReturn();

        assertSinFugaDeInformacionInterna(
                loginInyeccion
                        .getResponse()
                        .getContentAsString()
        );

        String token = obtenerAccessTokenAdmin();

        for (String payload : List.of(
                PAYLOAD_OR,
                PAYLOAD_DROP,
                PAYLOAD_UNION
        )) {

            MvcResult result = mockMvc.perform(
                            get(ENDPOINT_RESUMEN_ESPECIES)
                                    .header(
                                            "Authorization",
                                            "Bearer " + token
                                    )
                                    .param(
                                            "duenioId",
                                            payload
                                    )
                    )
                    .andReturn();

            assertSinFugaDeInformacionInterna(
                    result.getResponse()
                            .getContentAsString()
            );
        }
    }

    private void assertSinFugaDeInformacionInterna(
            String body
    ) {
        String enMinusculas =
                body.toLowerCase();

        for (String patron : PATRONES_FUGA_INTERNA) {
            assertFalse(
                    enMinusculas.contains(
                            patron.toLowerCase()
                    ),
                    "La respuesta filtro informacion interna ('"
                            + patron
                            + "'): "
                            + body
            );
        }
    }

    private String obtenerAccessTokenAdmin()
            throws Exception {

        MvcResult loginResult = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {"email":"%s","password":"%s"}
                                        """.formatted(
                                        EMAIL_VALIDO,
                                        PASSWORD_VALIDO
                                ))
                )
                .andExpect(status().isOk())
                .andReturn();

        return extractCookieValue(
                loginResult,
                "access_token"
        );
    }

    private ResultActions loginDesde(
            String email,
            String password,
            String remoteAddr
    ) throws Exception {

        return mockMvc.perform(
                post("/api/auth/login")
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .with(request -> {
                            request.setRemoteAddr(remoteAddr);
                            return request;
                        })
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(
                                email,
                                password
                        ))
        );
    }

    private String extractCookieValue(
            MvcResult result,
            String cookieName
    ) {
        List<String> setCookieHeaders =
                result.getResponse()
                        .getHeaders(HttpHeaders.SET_COOKIE);

        String header = setCookieHeaders.stream()
                .filter(value ->
                        value.startsWith(
                                cookieName + "="
                        ))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "No se encontró la cookie '"
                                        + cookieName
                                        + "' en las cabeceras Set-Cookie"
                        )
                );

        int separatorIndex =
                header.indexOf(';');

        String pair =
                separatorIndex >= 0
                        ? header.substring(
                                0,
                                separatorIndex
                        )
                        : header;

        return pair.substring(
                cookieName.length() + 1
        );
    }
}