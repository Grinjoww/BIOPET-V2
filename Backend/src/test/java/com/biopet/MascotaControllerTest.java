package com.biopet;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MascotaControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MascotaRepository mascotaRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockBean TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        mascotaRepository.deleteAll();
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
    void buscarMascotaInexistenteDevuelveProblemDetail() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String token = extractCookieValue(loginResult, "access_token");

        mockMvc.perform(get("/api/mascotas/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/mascotas/999999"));
    }

    @Test
    void crearMascotaConRolInsuficienteDevuelveProblemDetail() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Dueño Real","email":"dueno@biopet.com","password":"ClaveDueno123*","rol":"ROLE_DUENO"}
                                """))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dueno@biopet.com","password":"ClaveDueno123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String tokenDueno = extractCookieValue(loginResult, "access_token");

        mockMvc.perform(post("/api/mascotas")
                        .header("Authorization", "Bearer " + tokenDueno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duenioId":1,"nombre":"Firulais","especie":"Perro","raza":"Mestizo","fechaNacimiento":"2020-01-01"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"))
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/mascotas"));
    }

    @Test
    void duenoSoloVeSusPropiasMascotasEnListado() throws Exception {
        Long dueno1Id = registrarDuenoYObtenerId("listado.dueno1@biopet.com", "ClaveDueno123*");
        Long dueno2Id = registrarDuenoYObtenerId("listado.dueno2@biopet.com", "ClaveDueno456*");

        String tokenAdmin = extractCookieValue(iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"), "access_token");
        crearMascota(tokenAdmin, dueno1Id, "Firulais");
        crearMascota(tokenAdmin, dueno2Id, "Michi");

        String tokenDueno1 = extractCookieValue(iniciarSesion("listado.dueno1@biopet.com", "ClaveDueno123*"), "access_token");

        mockMvc.perform(get("/api/mascotas")
                        .header("Authorization", "Bearer " + tokenDueno1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Firulais"))
                .andExpect(jsonPath("$.content[0].duenioId").value(dueno1Id));
    }

    @Test
    void adminConservaListadoGlobalDeMascotas() throws Exception {
        Long dueno1Id = registrarDuenoYObtenerId("global.dueno1@biopet.com", "ClaveDueno123*");
        Long dueno2Id = registrarDuenoYObtenerId("global.dueno2@biopet.com", "ClaveDueno456*");

        String tokenAdmin = extractCookieValue(iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"), "access_token");
        crearMascota(tokenAdmin, dueno1Id, "Rocky");
        crearMascota(tokenAdmin, dueno2Id, "Luna");

        mockMvc.perform(get("/api/mascotas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void dosDuenosConMismaPaginaNoComparenResultadosDeCache() throws Exception {
        Long dueno1Id = registrarDuenoYObtenerId("cache.dueno1@biopet.com", "ClaveDueno123*");
        Long dueno2Id = registrarDuenoYObtenerId("cache.dueno2@biopet.com", "ClaveDueno456*");

        String tokenAdmin = extractCookieValue(iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"), "access_token");
        crearMascota(tokenAdmin, dueno1Id, "Toby");
        crearMascota(tokenAdmin, dueno2Id, "Nina");

        String tokenDueno1 = extractCookieValue(iniciarSesion("cache.dueno1@biopet.com", "ClaveDueno123*"), "access_token");
        String tokenDueno2 = extractCookieValue(iniciarSesion("cache.dueno2@biopet.com", "ClaveDueno456*"), "access_token");

        mockMvc.perform(get("/api/mascotas?page=0&size=10")
                        .header("Authorization", "Bearer " + tokenDueno1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Toby"));

        mockMvc.perform(get("/api/mascotas?page=0&size=10")
                        .header("Authorization", "Bearer " + tokenDueno2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Nina"));
    }

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

    private MvcResult iniciarSesion(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void crearMascota(String tokenAdmin, Long duenioId, String nombre) throws Exception {
        mockMvc.perform(post("/api/mascotas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duenioId":%d,"nombre":"%s","especie":"Perro","raza":"Mestizo","fechaNacimiento":"2020-01-01"}
                                """.formatted(duenioId, nombre)))
                .andExpect(status().isCreated());
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
