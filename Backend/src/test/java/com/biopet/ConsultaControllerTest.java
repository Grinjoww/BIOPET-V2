package com.biopet;

import com.biopet.entity.Consulta;
import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.CitaRepository;
import com.biopet.repository.ConsultaRepository;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsultaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    MascotaRepository mascotaRepository;

    @Autowired
    ConsultaRepository consultaRepository;

    @Autowired
    CitaRepository citaRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockBean
    TokenBlacklistService tokenBlacklistService;

    private Long veterinarioId;
    private Long mascotaId;

    @BeforeEach
    void setUp() {
        consultaRepository.deleteAll();
        citaRepository.deleteAll();
        mascotaRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario admin = Usuario.builder()
                .nombre("Jaime Mariscal")
                .email("jaime@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build();
        usuarioRepository.save(admin);

        Usuario veterinario = Usuario.builder()
                .nombre("Vet Real")
                .email("vet@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveVet123*"))
                .rol(Rol.ROLE_VETERINARIO)
                .activo(true)
                .build();

        veterinarioId = usuarioRepository.save(veterinario).getId();

        Usuario dueno = Usuario.builder()
                .nombre("Dueño Real")
                .email("dueno@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveDueno123*"))
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build();

        Usuario duenoGuardado = usuarioRepository.save(dueno);

        Mascota mascota = Mascota.builder()
                .duenio(duenoGuardado)
                .nombre("Firulais")
                .especie("Perro")
                .raza("Mestizo")
                .fechaNacimiento(java.time.LocalDate.of(2020, 1, 1))
                .activo(true)
                .build();

        mascotaId = mascotaRepository.save(mascota).getId();

        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void adminCreaConsultaExitosamente() throws Exception {
        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"veterinarioId":%d,"fechaConsulta":"%s","motivo":"Chequeo general"}
                                """.formatted(mascotaId, veterinarioId, Instant.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mascotaId").value(mascotaId))
                .andExpect(jsonPath("$.veterinarioId").value(veterinarioId))
                .andExpect(jsonPath("$.motivo").value("Chequeo general"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void duenoNoPuedeCrearConsultaDevuelve403() throws Exception {
        String tokenDueno = extractCookieValue(
                iniciarSesion("dueno@biopet.com", "ClaveDueno123*"),
                "access_token"
        );

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", "Bearer " + tokenDueno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"veterinarioId":%d,"fechaConsulta":"%s","motivo":"Chequeo general"}
                                """.formatted(mascotaId, veterinarioId, Instant.now())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));
    }

    @Test
    void buscarConsultaInexistenteDevuelve404() throws Exception {
        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas/999999")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"))
                .andExpect(jsonPath("$.instance").value("/api/consultas/999999"));
    }

    @Test
    void crearConsultaConCamposInvalidosDevuelve422() throws Exception {
        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"veterinarioId":%d,"fechaConsulta":"%s","motivo":""}
                                """.formatted(mascotaId, veterinarioId, Instant.now())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:validation"))
                .andExpect(jsonPath("$.errors.motivo").isArray());
    }

    @Test
    void duenoDeOtraMascotaNoPuedeVerConsultaAjena() throws Exception {
        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"veterinarioId":%d,"fechaConsulta":"%s","motivo":"Chequeo general"}
                                """.formatted(mascotaId, veterinarioId, Instant.now())))
                .andExpect(status().isCreated());

        Long consultaId = consultaRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Consulta no fue creada"))
                .getId();

        Usuario otroDueno = Usuario.builder()
                .nombre("Otro Dueño")
                .email("otro.dueno@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveOtro123*"))
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build();

        usuarioRepository.save(otroDueno);

        String tokenOtroDueno = extractCookieValue(
                iniciarSesion("otro.dueno@biopet.com", "ClaveOtro123*"),
                "access_token"
        );

        mockMvc.perform(get("/api/consultas/" + consultaId)
                        .header("Authorization", "Bearer " + tokenOtroDueno))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEliminaConsultaExitosamente() throws Exception {
        String tokenAdmin = extractCookieValue(
                iniciarSesion("jaime@biopet.com", "ClaveCorrecta123*"),
                "access_token"
        );

        mockMvc.perform(post("/api/consultas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"veterinarioId":%d,"fechaConsulta":"%s","motivo":"Chequeo general"}
                                """.formatted(mascotaId, veterinarioId, Instant.now())))
                .andExpect(status().isCreated());

        Long consultaId = consultaRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Consulta no fue creada"))
                .getId();

        mockMvc.perform(delete("/api/consultas/" + consultaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        Consulta eliminada = consultaRepository.findById(consultaId)
                .orElseThrow(() ->
                        new AssertionError("Consulta eliminada físicamente: " + consultaId)
                );

        assertFalse(eliminada.isActivo());
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
        List<String> setCookieHeaders =
                result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);

        String header = setCookieHeaders.stream()
                .filter(value -> value.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "No se encontró la cookie '" +
                                        cookieName +
                                        "' en las cabeceras Set-Cookie"
                        )
                );

        int separatorIndex = header.indexOf(';');

        String pair = separatorIndex >= 0
                ? header.substring(0, separatorIndex)
                : header;

        return pair.substring(cookieName.length() + 1);
    }
}