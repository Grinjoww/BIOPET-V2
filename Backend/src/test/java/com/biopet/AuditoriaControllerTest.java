package com.biopet;

import com.biopet.audit.repository.AuditoriaEventoRepository;
import com.biopet.repository.MascotaRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/admin/auditoria (solo lectura, solo ADMIN). Prueba tanto los
 * eventos NOMBRADOS (LOGIN_SUCCESS, reutilizado de AuthenticationAuditService)
 * como la captura GENERICA de AuditoriaHttpFilter sobre un modulo
 * cualquiera ajeno a respaldos -aqui, POST /api/mascotas- SIN que este test
 * ni el filtro toquen un solo archivo de ese controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditoriaControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MascotaRepository mascotaRepository;
    @Autowired AuditoriaEventoRepository auditoriaEventoRepository;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @MockBean TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        mascotaRepository.deleteAll();
        usuarioRepository.deleteAll();
        auditoriaEventoRepository.deleteAll();

        com.biopet.entity.Usuario admin = com.biopet.entity.Usuario.builder()
                .nombre("Admin Auditoria")
                .email("admin.auditoria@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(com.biopet.entity.Rol.ROLE_ADMIN)
                .activo(true)
                .build();
        usuarioRepository.save(admin);
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void noAdminRecibe403AlConsultarAuditoria() throws Exception {
        Long duenoId = registrarDuenoYObtenerId("auditoria.dueno1@biopet.com", "ClaveDueno123*");
        String tokenDueno = tokenDe("auditoria.dueno1@biopet.com", "ClaveDueno123*");

        mockMvc.perform(get("/api/admin/auditoria").header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));

        org.assertj.core.api.Assertions.assertThat(duenoId).isPositive();
    }

    @Test
    void loginGeneraUnEventoLoginSuccessConsultablePorAdmin() throws Exception {
        String tokenAdmin = tokenDe("admin.auditoria@biopet.com", "ClaveCorrecta123*");

        mockMvc.perform(get("/api/admin/auditoria")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .param("accion", "LOGIN_SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accion").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.content[0].usuarioEmail").value("admin.auditoria@biopet.com"))
                .andExpect(jsonPath("$.content[0].modulo").value("auth"))
                .andExpect(jsonPath("$.content[0].resultado").value("SUCCESS"));
    }

    @Test
    void crearMascotaQuedaAuditadaGenericamenteSinTocarMascotaController() throws Exception {
        String tokenAdmin = tokenDe("admin.auditoria@biopet.com", "ClaveCorrecta123*");
        Long duenoId = registrarDuenoYObtenerId("auditoria.dueno2@biopet.com", "ClaveDueno123*");

        mockMvc.perform(post("/api/mascotas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duenioId":%d,"nombre":"Firulais","especie":"Perro","raza":"Mestizo","fechaNacimiento":"2020-01-01"}
                                """.formatted(duenoId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/auditoria")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .param("accion", "POST")
                        .param("modulo", "mascotas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].accion").value("POST"))
                .andExpect(jsonPath("$.content[0].modulo").value("mascotas"))
                .andExpect(jsonPath("$.content[0].recurso").value("/api/mascotas"))
                .andExpect(jsonPath("$.content[0].metodoHttp").value("POST"))
                .andExpect(jsonPath("$.content[0].resultado").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].statusHttp").value(201))
                .andExpect(jsonPath("$.content[0].usuarioEmail").value("admin.auditoria@biopet.com"));
    }

    @Test
    void unIntentoRechazadoQuedaAuditadoComoFailureConSuStatus() throws Exception {
        Long dueno1Id = registrarDuenoYObtenerId("auditoria.dueno3@biopet.com", "ClaveDueno123*");
        String tokenDueno = tokenDe("auditoria.dueno3@biopet.com", "ClaveDueno123*");

        // DUENO no puede crear mascotas (solo ADMIN/VETERINARIO/AUXILIAR) -> 403.
        mockMvc.perform(post("/api/mascotas")
                        .header("Authorization", "Bearer " + tokenDueno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duenioId":%d,"nombre":"Michi","especie":"Gato","raza":"Siames","fechaNacimiento":"2021-01-01"}
                                """.formatted(dueno1Id)))
                .andExpect(status().isForbidden());

        String tokenAdmin = tokenDe("admin.auditoria@biopet.com", "ClaveCorrecta123*");
        mockMvc.perform(get("/api/admin/auditoria")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .param("usuario", "auditoria.dueno3@biopet.com")
                        .param("accion", "POST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].resultado").value("FAILURE"))
                .andExpect(jsonPath("$.content[0].statusHttp").value(403));
    }

    @Test
    void filtroPorUsuarioDevuelveSoloLosEventosDeEseUsuario() throws Exception {
        registrarDuenoYObtenerId("auditoria.filtro1@biopet.com", "ClaveDueno123*");
        registrarDuenoYObtenerId("auditoria.filtro2@biopet.com", "ClaveDueno123*");
        tokenDe("auditoria.filtro1@biopet.com", "ClaveDueno123*");
        tokenDe("auditoria.filtro2@biopet.com", "ClaveDueno123*");

        String tokenAdmin = tokenDe("admin.auditoria@biopet.com", "ClaveCorrecta123*");
        MvcResult resultado = mockMvc.perform(get("/api/admin/auditoria")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .param("usuario", "auditoria.filtro1@biopet.com")
                        .param("accion", "LOGIN_SUCCESS")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();

        String cuerpo = resultado.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(cuerpo).contains("auditoria.filtro1@biopet.com");
        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("auditoria.filtro2@biopet.com");
    }

    @Test
    void filtroPorRangoDeFechasAceptaInstantIso8601() throws Exception {
        String tokenAdmin = tokenDe("admin.auditoria@biopet.com", "ClaveCorrecta123*");
        java.time.Instant ahora = java.time.Instant.now();

        // Rango que SI incluye "ahora" (el login de arriba).
        mockMvc.perform(get("/api/admin/auditoria")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .param("accion", "LOGIN_SUCCESS")
                        .param("desde", ahora.minusSeconds(60).toString())
                        .param("hasta", ahora.plusSeconds(60).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Rango que NO incluye "ahora" (todo en el pasado).
        mockMvc.perform(get("/api/admin/auditoria")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .param("accion", "LOGIN_SUCCESS")
                        .param("desde", ahora.minusSeconds(3600).toString())
                        .param("hasta", ahora.minusSeconds(1800).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void opcionesFiltroDevuelveValoresRealmentePresentes() throws Exception {
        String tokenAdmin = tokenDe("admin.auditoria@biopet.com", "ClaveCorrecta123*");

        mockMvc.perform(get("/api/admin/auditoria/opciones").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarios").value(org.hamcrest.Matchers.hasItem("admin.auditoria@biopet.com")))
                .andExpect(jsonPath("$.acciones").value(org.hamcrest.Matchers.hasItem("LOGIN_SUCCESS")))
                .andExpect(jsonPath("$.modulos").value(org.hamcrest.Matchers.hasItem("auth")));
    }

    @Test
    void listadoEsPaginado() throws Exception {
        String tokenAdmin = tokenDe("admin.auditoria@biopet.com", "ClaveCorrecta123*");
        // El propio login de arriba ya genero al menos 1 evento; con size=1
        // el listado debe reportar exactamente 1 elemento por pagina.
        mockMvc.perform(get("/api/admin/auditoria")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ---------- Helpers ----------

    private Long registrarDuenoYObtenerId(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Dueño Prueba","email":"%s","password":"%s","rol":"ROLE_DUENO"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated());
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("Usuario no encontrado tras registro: " + email))
                .getId();
    }

    private String tokenDe(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return extractCookieValue(loginResult, "access_token");
    }

    private String extractCookieValue(MvcResult result, String cookieName) {
        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String header = setCookieHeaders.stream()
                .filter(value -> value.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró la cookie '" + cookieName + "'"));
        int separatorIndex = header.indexOf(';');
        String pair = separatorIndex >= 0 ? header.substring(0, separatorIndex) : header;
        return pair.substring(cookieName.length() + 1);
    }
}
