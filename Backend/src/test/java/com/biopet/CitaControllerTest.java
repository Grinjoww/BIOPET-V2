package com.biopet;

import com.biopet.entity.Cita;
import com.biopet.entity.EstadoCita;
import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.CitaRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre el CRUD de Citas (agendamiento previo de atención veterinaria). No debe
 * confundirse con "Consulta" (registro clínico posterior, módulo de otro
 * integrante del equipo, fuera de alcance aquí).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CitaControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MascotaRepository mascotaRepository;
    @Autowired CitaRepository citaRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockBean TokenBlacklistService tokenBlacklistService;

    private static final String EMAIL_ADMIN = "jaime@biopet.com";
    private static final String PASSWORD_ADMIN = "ClaveCorrecta123*";

    private Long veterinarioId;
    private Long duenoId;
    private Long mascotaId;

    @BeforeEach
    void setUp() {
        citaRepository.deleteAll();
        mascotaRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario admin = Usuario.builder()
                .nombre("Jaime Mariscal")
                .email(EMAIL_ADMIN)
                .passwordHash(passwordEncoder.encode(PASSWORD_ADMIN))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build();
        usuarioRepository.save(admin);
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);

        veterinarioId = crearUsuarioConRolYObtenerId("vet.principal@biopet.com", "ClaveVet123*", Rol.ROLE_VETERINARIO);
        duenoId = crearUsuarioConRolYObtenerId("dueno.principal@biopet.com", "ClaveDueno123*", Rol.ROLE_DUENO);
        mascotaId = crearMascotaYObtenerId(duenoId, "Firulais");
    }

    // ---------- listar / buscar ----------

    @Test
    void adminListaCitas() throws Exception {
        crearCitaYObtenerId(mascotaId, veterinarioId);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/citas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void obtenerCitaPorIdDevuelve200() throws Exception {
        Long citaId = crearCitaYObtenerId(mascotaId, veterinarioId);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/citas/" + citaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(citaId))
                .andExpect(jsonPath("$.mascotaId").value(mascotaId))
                .andExpect(jsonPath("$.veterinarioId").value(veterinarioId))
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"));
    }

    @Test
    void citaInexistenteDevuelve404() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/citas/999999")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"))
                .andExpect(jsonPath("$.instance").value("/api/citas/999999"));
    }

    @Test
    void duenoConsultaCitaDeSuPropiaMascota() throws Exception {
        Long citaId = crearCitaYObtenerId(mascotaId, veterinarioId);
        String tokenDueno = extractCookieValue(iniciarSesion("dueno.principal@biopet.com", "ClaveDueno123*"), "access_token");

        mockMvc.perform(get("/api/citas/" + citaId)
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(citaId));
    }

    @Test
    void duenoNoPuedeConsultarCitaDeMascotaAjena() throws Exception {
        Long otroDuenoId = crearUsuarioConRolYObtenerId("otro.dueno@biopet.com", "ClaveDueno456*", Rol.ROLE_DUENO);
        Long otraMascotaId = crearMascotaYObtenerId(otroDuenoId, "Michi");
        Long citaAjenaId = crearCitaYObtenerId(otraMascotaId, veterinarioId);

        String tokenDueno = extractCookieValue(iniciarSesion("dueno.principal@biopet.com", "ClaveDueno123*"), "access_token");

        mockMvc.perform(get("/api/citas/" + citaAjenaId)
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));
    }

    @Test
    void duenoSoloVeSusPropiasCitasEnListado() throws Exception {
        crearCitaYObtenerId(mascotaId, veterinarioId);
        Long otroDuenoId = crearUsuarioConRolYObtenerId("listado.otro.dueno@biopet.com", "ClaveDueno456*", Rol.ROLE_DUENO);
        Long otraMascotaId = crearMascotaYObtenerId(otroDuenoId, "Michi");
        crearCitaYObtenerId(otraMascotaId, veterinarioId);

        String tokenDueno = extractCookieValue(iniciarSesion("dueno.principal@biopet.com", "ClaveDueno123*"), "access_token");

        mockMvc.perform(get("/api/citas")
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].mascotaId").value(mascotaId));
    }

    // ---------- crear ----------

    @Test
    void crearCitaValidaDevuelve201() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(post("/api/citas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, veterinarioId, "PROGRAMADA", "Control anual")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mascotaId").value(mascotaId))
                .andExpect(jsonPath("$.veterinarioId").value(veterinarioId))
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void crearCitaForzandoEstadoDistintoIgnoraElEstadoEnviado() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(post("/api/citas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, veterinarioId, "COMPLETADA", "Intento de forzar estado")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"));
    }

    @Test
    void auxiliarPuedeCrearCita() throws Exception {
        crearUsuarioConRol("aux.crea@biopet.com", "ClaveAux123*", Rol.ROLE_AUXILIAR);
        String tokenAuxiliar = extractCookieValue(iniciarSesion("aux.crea@biopet.com", "ClaveAux123*"), "access_token");

        mockMvc.perform(post("/api/citas")
                        .header("Authorization", "Bearer " + tokenAuxiliar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, veterinarioId, "PROGRAMADA", "Vacunación")))
                .andExpect(status().isCreated());
    }

    @Test
    void crearCitaConMascotaInexistenteDevuelve404() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(post("/api/citas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(999999L, veterinarioId, "PROGRAMADA", "Control")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"))
                .andExpect(jsonPath("$.instance").value("/api/citas"));
    }

    @Test
    void crearCitaConVeterinarioInexistenteDevuelve404() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(post("/api/citas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, 999999L, "PROGRAMADA", "Control")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"))
                .andExpect(jsonPath("$.instance").value("/api/citas"));
    }

    @Test
    void crearCitaConUsuarioNoVeterinarioAsignadoDevuelve400() throws Exception {
        Long auxiliarId = crearUsuarioConRolYObtenerId("aux.no.vet@biopet.com", "ClaveAux123*", Rol.ROLE_AUXILIAR);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(post("/api/citas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, auxiliarId, "PROGRAMADA", "Control")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:bad-request"))
                .andExpect(jsonPath("$.instance").value("/api/citas"));
    }

    @Test
    void crearCitaConCamposInvalidosDevuelve422() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(post("/api/citas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaHora":null,"estado":"PROGRAMADA"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:validation"))
                .andExpect(jsonPath("$.instance").value("/api/citas"))
                .andExpect(jsonPath("$.errors.mascotaId").isArray())
                .andExpect(jsonPath("$.errors.veterinarioId").isArray())
                .andExpect(jsonPath("$.errors.fechaHora").isArray());
    }

    @Test
    void duenoNoPuedeCrearCitaDevuelve403() throws Exception {
        String tokenDueno = extractCookieValue(iniciarSesion("dueno.principal@biopet.com", "ClaveDueno123*"), "access_token");

        mockMvc.perform(post("/api/citas")
                        .header("Authorization", "Bearer " + tokenDueno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, veterinarioId, "PROGRAMADA", "Intento no autorizado")))
                .andExpect(status().isForbidden());
    }

    @Test
    void veterinarioNoPuedeCrearCitaDevuelve403() throws Exception {
        String tokenVeterinario = extractCookieValue(iniciarSesion("vet.principal@biopet.com", "ClaveVet123*"), "access_token");

        mockMvc.perform(post("/api/citas")
                        .header("Authorization", "Bearer " + tokenVeterinario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, veterinarioId, "PROGRAMADA", "Intento no autorizado")))
                .andExpect(status().isForbidden());
    }

    // ---------- actualizar ----------

    @Test
    void adminActualizaCitaDevuelve200() throws Exception {
        Long citaId = crearCitaYObtenerId(mascotaId, veterinarioId);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(put("/api/citas/" + citaId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, veterinarioId, "CANCELADA", "Cliente canceló")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.motivo").value("Cliente canceló"));
    }

    @Test
    void actualizarCitaInexistenteDevuelve404() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(put("/api/citas/999999")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, veterinarioId, "PROGRAMADA", "N/A")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:not-found"));
    }

    @Test
    void veterinarioActualizaSuPropiaCitaDevuelve200() throws Exception {
        Long citaId = crearCitaYObtenerId(mascotaId, veterinarioId);
        String tokenVeterinario = extractCookieValue(iniciarSesion("vet.principal@biopet.com", "ClaveVet123*"), "access_token");

        mockMvc.perform(put("/api/citas/" + citaId)
                        .header("Authorization", "Bearer " + tokenVeterinario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, veterinarioId, "COMPLETADA", "Atendido")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));
    }

    @Test
    void veterinarioNoPuedeActualizarCitaDeOtroVeterinarioDevuelve403() throws Exception {
        Long otroVeterinarioId = crearUsuarioConRolYObtenerId("otro.vet@biopet.com", "ClaveVet456*", Rol.ROLE_VETERINARIO);
        Long citaId = crearCitaYObtenerId(mascotaId, otroVeterinarioId);

        String tokenVeterinario = extractCookieValue(iniciarSesion("vet.principal@biopet.com", "ClaveVet123*"), "access_token");

        mockMvc.perform(put("/api/citas/" + citaId)
                        .header("Authorization", "Bearer " + tokenVeterinario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, otroVeterinarioId, "CANCELADA", "No autorizado")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));
    }

    @Test
    void duenoNoPuedeActualizarCitaDevuelve403() throws Exception {
        Long citaId = crearCitaYObtenerId(mascotaId, veterinarioId);
        String tokenDueno = extractCookieValue(iniciarSesion("dueno.principal@biopet.com", "ClaveDueno123*"), "access_token");

        mockMvc.perform(put("/api/citas/" + citaId)
                        .header("Authorization", "Bearer " + tokenDueno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCita(mascotaId, veterinarioId, "CANCELADA", "No autorizado")))
                .andExpect(status().isForbidden());
    }

    // ---------- eliminar ----------

    @Test
    void adminEliminaCitaDevuelve204YBajaLogica() throws Exception {
        Long citaId = crearCitaYObtenerId(mascotaId, veterinarioId);
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(delete("/api/citas/" + citaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        Cita citaEliminada = citaRepository.findById(citaId)
                .orElseThrow(() -> new AssertionError("La cita fue eliminada físicamente de la base de datos: " + citaId));
        assertFalse(citaEliminada.isActivo());

        mockMvc.perform(get("/api/citas/" + citaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminarCitaInexistenteDevuelve404() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(delete("/api/citas/999999")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void auxiliarNoPuedeEliminarCitaDevuelve403() throws Exception {
        Long citaId = crearCitaYObtenerId(mascotaId, veterinarioId);
        crearUsuarioConRol("aux.elimina@biopet.com", "ClaveAux123*", Rol.ROLE_AUXILIAR);
        String tokenAuxiliar = extractCookieValue(iniciarSesion("aux.elimina@biopet.com", "ClaveAux123*"), "access_token");

        mockMvc.perform(delete("/api/citas/" + citaId)
                        .header("Authorization", "Bearer " + tokenAuxiliar))
                .andExpect(status().isForbidden());
    }

    // ---------- autenticación ----------

    @Test
    void accesoSinAutenticacionDevuelve401() throws Exception {
        mockMvc.perform(get("/api/citas"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/citas"));
    }

    // ---------- helpers ----------

    private String cuerpoCita(Long mascotaId, Long veterinarioId, String estado, String motivo) {
        String fechaHora = Instant.now().plus(3, ChronoUnit.DAYS).toString();
        return """
                {"mascotaId":%d,"veterinarioId":%d,"fechaHora":"%s","estado":"%s","motivo":"%s"}
                """.formatted(mascotaId, veterinarioId, fechaHora, estado, motivo);
    }

    private Long crearCitaYObtenerId(Long mascotaId, Long veterinarioId) {
        Mascota mascota = mascotaRepository.findById(mascotaId).orElseThrow();
        Usuario veterinario = usuarioRepository.findById(veterinarioId).orElseThrow();
        Cita cita = Cita.builder()
                .mascota(mascota)
                .veterinario(veterinario)
                .fechaHora(Instant.now().plus(2, ChronoUnit.DAYS))
                .estado(EstadoCita.PROGRAMADA)
                .motivo("Control de rutina")
                .activo(true)
                .build();
        return citaRepository.save(cita).getId();
    }

    private Long crearMascotaYObtenerId(Long duenioId, String nombre) {
        Usuario duenio = usuarioRepository.findById(duenioId).orElseThrow();
        Mascota mascota = Mascota.builder()
                .duenio(duenio)
                .nombre(nombre)
                .especie("Perro")
                .raza("Mestizo")
                .fechaNacimiento(java.time.LocalDate.of(2020, 1, 1))
                .activo(true)
                .build();
        return mascotaRepository.save(mascota).getId();
    }

    private Long crearUsuarioConRolYObtenerId(String email, String password, Rol rol) {
        crearUsuarioConRol(email, password, rol);
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("Usuario no encontrado tras crearlo: " + email))
                .getId();
    }

    private void crearUsuarioConRol(String email, String password, Rol rol) {
        Usuario usuario = Usuario.builder()
                .nombre("Usuario Prueba")
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .activo(true)
                .build();
        usuarioRepository.save(usuario);
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
