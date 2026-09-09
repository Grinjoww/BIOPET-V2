package com.biopet;

import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.entity.Vacuna;
import com.biopet.repository.CitaRepository;
import com.biopet.repository.ConsultaRepository;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import com.biopet.repository.VacunaRepository;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Espejo de MascotaControllerTest, adaptado a /api/vacunas (rama
 * zaida/u4-vacunas-frontend-postman). Cada test crea su propio dueño +
 * mascota vía la API real (nunca insertando la mascota directo por
 * repositorio) para no desincronizarse de las reglas del backend.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VacunaControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MascotaRepository mascotaRepository;
    @Autowired VacunaRepository vacunaRepository;
    @Autowired CitaRepository citaRepository;
    @Autowired ConsultaRepository consultaRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockBean TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        // Consultas y citas se eliminan antes que mascotas porque ambas tablas
        // contienen una FK hacia mascotas (ver MascotaControllerTest).
        vacunaRepository.deleteAll();
        consultaRepository.deleteAll();
        citaRepository.deleteAll();
        mascotaRepository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario admin = Usuario.builder()
                .nombre("Zaida Admin")
                .email("admin.vacunas@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build();
        usuarioRepository.save(admin);
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void adminCreaVacunaExitosamente() throws Exception {
        Long duenoId = registrarDuenoYObtenerId("vacuna.crear.dueno@biopet.com", "ClaveDueno123*");
        String tokenAdmin = tokenDe("admin.vacunas@biopet.com", "ClaveCorrecta123*");
        Long mascotaId = crearMascotaYObtenerId(tokenAdmin, duenoId, "Firulais");

        mockMvc.perform(post("/api/vacunas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"tipo":"Antirrábica","fechaAplicacion":"2026-01-10","observaciones":"Sin reacciones"}
                                """.formatted(mascotaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mascotaId").value(mascotaId))
                .andExpect(jsonPath("$.tipo").value("Antirrábica"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void duenoIntentaCrearVacunaEsRechazadoPorRol() throws Exception {
        Long duenoId = registrarDuenoYObtenerId("vacuna.rol.dueno@biopet.com", "ClaveDueno123*");
        String tokenAdmin = tokenDe("admin.vacunas@biopet.com", "ClaveCorrecta123*");
        Long mascotaId = crearMascotaYObtenerId(tokenAdmin, duenoId, "Michi");

        String tokenDueno = tokenDe("vacuna.rol.dueno@biopet.com", "ClaveDueno123*");

        mockMvc.perform(post("/api/vacunas")
                        .header("Authorization", "Bearer " + tokenDueno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"tipo":"Antirrábica","fechaAplicacion":"2026-01-10"}
                                """.formatted(mascotaId)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));
    }

    @Test
    void crearVacunaConMascotaInexistenteDevuelve404() throws Exception {
        String tokenAdmin = tokenDe("admin.vacunas@biopet.com", "ClaveCorrecta123*");

        mockMvc.perform(post("/api/vacunas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":999999,"tipo":"Antirrábica","fechaAplicacion":"2026-01-10"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"));
    }

    @Test
    void crearVacunaConCamposInvalidosDevuelve422() throws Exception {
        Long duenoId = registrarDuenoYObtenerId("vacuna.invalida.dueno@biopet.com", "ClaveDueno123*");
        String tokenAdmin = tokenDe("admin.vacunas@biopet.com", "ClaveCorrecta123*");
        Long mascotaId = crearMascotaYObtenerId(tokenAdmin, duenoId, "Toby");

        mockMvc.perform(post("/api/vacunas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"tipo":"","fechaAplicacion":"2026-01-10"}
                                """.formatted(mascotaId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:validation"))
                .andExpect(jsonPath("$.errors.tipo").isArray());
    }

    @Test
    void duenoSoloVeVacunasDeSusPropiasMascotas() throws Exception {
        Long dueno1Id = registrarDuenoYObtenerId("vacuna.listado.dueno1@biopet.com", "ClaveDueno123*");
        Long dueno2Id = registrarDuenoYObtenerId("vacuna.listado.dueno2@biopet.com", "ClaveDueno456*");
        String tokenAdmin = tokenDe("admin.vacunas@biopet.com", "ClaveCorrecta123*");
        Long mascota1Id = crearMascotaYObtenerId(tokenAdmin, dueno1Id, "Rocky");
        Long mascota2Id = crearMascotaYObtenerId(tokenAdmin, dueno2Id, "Luna");
        crearVacuna(tokenAdmin, mascota1Id, "Antirrábica");
        crearVacuna(tokenAdmin, mascota2Id, "Parvovirus");

        String tokenDueno1 = tokenDe("vacuna.listado.dueno1@biopet.com", "ClaveDueno123*");

        mockMvc.perform(get("/api/vacunas").header("Authorization", "Bearer " + tokenDueno1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].mascotaId").value(mascota1Id));
    }

    @Test
    void duenoConsultaVacunaDeMascotaAjenaDevuelve403() throws Exception {
        Long dueno1Id = registrarDuenoYObtenerId("vacuna.ajena.dueno1@biopet.com", "ClaveDueno123*");
        Long dueno2Id = registrarDuenoYObtenerId("vacuna.ajena.dueno2@biopet.com", "ClaveDueno456*");
        String tokenAdmin = tokenDe("admin.vacunas@biopet.com", "ClaveCorrecta123*");
        Long mascota2Id = crearMascotaYObtenerId(tokenAdmin, dueno2Id, "Nina");
        Long vacunaId = crearVacunaYObtenerId(tokenAdmin, mascota2Id, "Antirrábica");

        String tokenDueno1 = tokenDe("vacuna.ajena.dueno1@biopet.com", "ClaveDueno123*");

        mockMvc.perform(get("/api/vacunas/" + vacunaId).header("Authorization", "Bearer " + tokenDueno1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));
    }

    @Test
    void adminActualizaVacunaExitosamente() throws Exception {
        Long duenoId = registrarDuenoYObtenerId("vacuna.actualizar.dueno@biopet.com", "ClaveDueno123*");
        String tokenAdmin = tokenDe("admin.vacunas@biopet.com", "ClaveCorrecta123*");
        Long mascotaId = crearMascotaYObtenerId(tokenAdmin, duenoId, "Rex");
        Long vacunaId = crearVacunaYObtenerId(tokenAdmin, mascotaId, "Antirrábica");

        mockMvc.perform(put("/api/vacunas/" + vacunaId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"tipo":"Refuerzo antirrábica","fechaAplicacion":"2026-02-01","proximaFecha":"2027-02-01"}
                                """.formatted(mascotaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Refuerzo antirrábica"))
                .andExpect(jsonPath("$.proximaFecha").value("2027-02-01"));
    }

    @Test
    void adminEliminaVacunaExitosamente() throws Exception {
        Long duenoId = registrarDuenoYObtenerId("vacuna.eliminar.dueno@biopet.com", "ClaveDueno123*");
        String tokenAdmin = tokenDe("admin.vacunas@biopet.com", "ClaveCorrecta123*");
        Long mascotaId = crearMascotaYObtenerId(tokenAdmin, duenoId, "Kitty");
        Long vacunaId = crearVacunaYObtenerId(tokenAdmin, mascotaId, "Antirrábica");

        mockMvc.perform(delete("/api/vacunas/" + vacunaId).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        Vacuna vacunaEliminada = vacunaRepository.findById(vacunaId)
                .orElseThrow(() -> new AssertionError("La vacuna fue eliminada físicamente: " + vacunaId));
        assertFalse(vacunaEliminada.isActivo());
    }

    @Test
    void veterinarioSoloVeSusPropiasVacunasEnListado_YAdminVeAmbas() throws Exception {
        // Corrección "demo local" (fase de roles): ROLE_VETERINARIO debe ver
        // únicamente las vacunas donde él es el veterinario asignado (antes
        // veía TODAS, de cualquier veterinario, sin ninguna restricción).
        String tokenAdmin = tokenDe("admin.vacunas@biopet.com", "ClaveCorrecta123*");
        Long duenoId = registrarDuenoYObtenerId("vacuna.rol.dueno@biopet.com", "ClaveDueno123*");
        Long mascotaId = crearMascotaYObtenerId(tokenAdmin, duenoId, "Firulais");
        Long vetAId = registrarVeterinarioYObtenerId(tokenAdmin, "vacuna.veta@biopet.com", "ClaveVetA123*");
        Long vetBId = registrarVeterinarioYObtenerId(tokenAdmin, "vacuna.vetb@biopet.com", "ClaveVetB123*");
        crearVacunaConVeterinarioYObtenerId(tokenAdmin, mascotaId, vetAId, "Antirrábica");
        crearVacunaConVeterinarioYObtenerId(tokenAdmin, mascotaId, vetBId, "Parvovirus");

        String tokenVetA = tokenDe("vacuna.veta@biopet.com", "ClaveVetA123*");
        mockMvc.perform(get("/api/vacunas").header("Authorization", "Bearer " + tokenVetA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].veterinarioId").value(vetAId));

        String tokenVetB = tokenDe("vacuna.vetb@biopet.com", "ClaveVetB123*");
        mockMvc.perform(get("/api/vacunas").header("Authorization", "Bearer " + tokenVetB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].veterinarioId").value(vetBId));

        mockMvc.perform(get("/api/vacunas").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtrarVacunasPorMascotaVeterinarioYTipo() throws Exception {
        String tokenAdmin = tokenDe("admin.vacunas@biopet.com", "ClaveCorrecta123*");
        Long duenoId = registrarDuenoYObtenerId("vacuna.filtro.dueno@biopet.com", "ClaveDueno123*");
        Long mascota1Id = crearMascotaYObtenerId(tokenAdmin, duenoId, "Rocky");
        Long mascota2Id = crearMascotaYObtenerId(tokenAdmin, duenoId, "Luna");
        Long vetId = registrarVeterinarioYObtenerId(tokenAdmin, "vacuna.filtro.vet@biopet.com", "ClaveVet123*");
        Long vacunaId1 = crearVacunaConVeterinarioYObtenerId(tokenAdmin, mascota1Id, vetId, "Antirrábica");
        crearVacuna(tokenAdmin, mascota2Id, "Parvovirus"); // sin veterinario asignado

        mockMvc.perform(get("/api/vacunas")
                        .param("mascotaId", mascota1Id.toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(vacunaId1));

        mockMvc.perform(get("/api/vacunas")
                        .param("veterinarioId", vetId.toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(vacunaId1));

        mockMvc.perform(get("/api/vacunas")
                        .param("tipo", "rrábica")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(vacunaId1));
    }

    @Test
    void buscarVacunaInexistenteDevuelve404() throws Exception {
        String tokenAdmin = tokenDe("admin.vacunas@biopet.com", "ClaveCorrecta123*");

        mockMvc.perform(get("/api/vacunas/999999").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"));
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

    private Long crearMascotaYObtenerId(String tokenAdmin, Long duenioId, String nombre) throws Exception {
        mockMvc.perform(post("/api/mascotas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duenioId":%d,"nombre":"%s","especie":"Perro","raza":"Mestizo","fechaNacimiento":"2020-01-01"}
                                """.formatted(duenioId, nombre)))
                .andExpect(status().isCreated());
        return mascotaRepository.findAll().stream()
                .filter(m -> m.getNombre().equals(nombre))
                .map(Mascota::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Mascota no encontrada tras crearla: " + nombre));
    }

    private void crearVacuna(String tokenAdmin, Long mascotaId, String tipo) throws Exception {
        mockMvc.perform(post("/api/vacunas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"tipo":"%s","fechaAplicacion":"2026-01-10"}
                                """.formatted(mascotaId, tipo)))
                .andExpect(status().isCreated());
    }

    private Long crearVacunaYObtenerId(String tokenAdmin, Long mascotaId, String tipo) throws Exception {
        crearVacuna(tokenAdmin, mascotaId, tipo);
        return vacunaRepository.findAll().stream()
                .filter(v -> v.getMascota().getId().equals(mascotaId) && v.getTipo().equals(tipo))
                .map(Vacuna::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Vacuna no encontrada tras crearla"));
    }

    private Long crearVacunaConVeterinarioYObtenerId(String tokenAdmin, Long mascotaId, Long veterinarioId, String tipo) throws Exception {
        mockMvc.perform(post("/api/vacunas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mascotaId":%d,"veterinarioId":%d,"tipo":"%s","fechaAplicacion":"2026-01-10"}
                                """.formatted(mascotaId, veterinarioId, tipo)))
                .andExpect(status().isCreated());
        return vacunaRepository.findAll().stream()
                .filter(v -> v.getMascota().getId().equals(mascotaId) && v.getTipo().equals(tipo))
                .map(Vacuna::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Vacuna no encontrada tras crearla"));
    }

    private Long registrarVeterinarioYObtenerId(String tokenAdmin, String email, String password) throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Veterinario Prueba","email":"%s","password":"%s","rol":"ROLE_VETERINARIO"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated());
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("Veterinario no encontrado tras crearlo: " + email))
                .getId();
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
