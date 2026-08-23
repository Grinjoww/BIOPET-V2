package com.biopet;

import com.biopet.entity.Cita;
import com.biopet.entity.Consulta;
import com.biopet.entity.EstadoCita;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/dashboard/resumen invoca fn_reporte_dashboard (procedimiento
 * PL/pgSQL real, ver V6__formalizar_procedimientos_jpa.sql) a través de
 * DashboardService/ProcedimientoBiopetRepository. El perfil "test" normal
 * (application-test.yml) corre contra H2 con Flyway deshabilitado —
 * suficiente para el resto de controllers, pero H2 no entiende PL/pgSQL
 * ni tiene la función/procedimiento creado. Por eso esta clase, igual que
 * ProcedimientosBiopetIntegrationTest/ResumenEspeciesIntegrationTest,
 * sustituye el datasource por PostgreSQL real (Testcontainers) y habilita
 * Flyway, conservando el resto del perfil "test" (JWT, cookies, caché)
 * para poder autenticar por HTTP igual que el resto de *ControllerTest.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("biopet_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        // application-test.yml fija driver-class-name=org.h2.Driver (perfil
        // "test" normal). Como aquí SÍ activamos ese perfil (para heredar
        // JWT/cookies/caché), hay que sobrescribir también el driver, o
        // Hibernate intenta abrir la URL de Postgres con el driver de H2.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

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
    VacunaRepository vacunaRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @MockBean
    TokenBlacklistService tokenBlacklistService;

    private static final String EMAIL_ADMIN = "admin-dashboard@biopet.ec";
    private static final String PASSWORD_ADMIN = "ClaveAdmin123*";
    private static final String EMAIL_VET = "vet-dashboard@biopet.ec";
    private static final String PASSWORD_VET = "ClaveVet123*";
    private static final String EMAIL_DUENO = "dueno-dashboard@biopet.ec";
    private static final String PASSWORD_DUENO = "ClaveDueno123*";

    private Usuario veterinario;

    @BeforeEach
    void setUp() {
        vacunaRepository.deleteAll();
        consultaRepository.deleteAll();
        citaRepository.deleteAll();
        mascotaRepository.deleteAll();
        usuarioRepository.deleteAll();

        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);

        usuarioRepository.save(Usuario.builder()
                .nombre("Admin Dashboard").email(EMAIL_ADMIN)
                .passwordHash(passwordEncoder.encode(PASSWORD_ADMIN))
                .rol(Rol.ROLE_ADMIN).activo(true).build());

        veterinario = usuarioRepository.save(Usuario.builder()
                .nombre("Vet Dashboard").email(EMAIL_VET)
                .passwordHash(passwordEncoder.encode(PASSWORD_VET))
                .rol(Rol.ROLE_VETERINARIO).activo(true).build());

        usuarioRepository.save(Usuario.builder()
                .nombre("Dueno Dashboard").email(EMAIL_DUENO)
                .passwordHash(passwordEncoder.encode(PASSWORD_DUENO))
                .rol(Rol.ROLE_DUENO).activo(true).build());
    }

    // ---------- caso feliz: numeros reales calculados por el procedimiento ----------

    @Test
    void adminObtieneResumenConDatosReales() throws Exception {
        Usuario duenio = usuarioRepository.findByEmail(EMAIL_DUENO).orElseThrow();
        Mascota mascota = mascotaRepository.save(Mascota.builder()
                .duenio(duenio).nombre("Rex").especie("Perro").raza("Mestizo")
                .fechaNacimiento(LocalDate.of(2020, 1, 1)).activo(true).build());

        citaRepository.save(Cita.builder()
                .mascota(mascota).veterinario(veterinario)
                .fechaHora(Instant.now().plus(2, ChronoUnit.DAYS))
                .estado(EstadoCita.PROGRAMADA).activo(true).build());

        consultaRepository.save(Consulta.builder()
                .mascota(mascota).veterinario(veterinario)
                .fechaConsulta(Instant.now())
                .motivo("Chequeo").activo(true).build());

        vacunaRepository.save(Vacuna.builder()
                .mascota(mascota).veterinario(veterinario).tipo("Rabia")
                .fechaAplicacion(LocalDate.now()).activo(true).build());

        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");
        LocalDate desde = LocalDate.now().minusDays(30);
        LocalDate hasta = LocalDate.now();

        mockMvc.perform(get("/api/dashboard/resumen")
                        .param("desde", desde.toString())
                        .param("hasta", hasta.toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value(desde.toString()))
                .andExpect(jsonPath("$.hasta").value(hasta.toString()))
                .andExpect(jsonPath("$.mascotasActivas").value(1))
                .andExpect(jsonPath("$.citasProgramadas").value(1))
                .andExpect(jsonPath("$.consultasEnRango").value(1))
                .andExpect(jsonPath("$.vacunasEnRango").value(1))
                .andExpect(jsonPath("$.mascotasSinConsulta").value(0));
    }

    /**
     * Regresión end-to-end (HTTP real, no solo repositorio) del bug de
     * pgJDBC 42.6.2 descrito en {@code ProcedimientosBiopetIntegrationTest.
     * reporteDashboard_15InvocacionesConsecutivasEnLaMismaConexion_...}:
     * con solicitudes secuenciales (sin concurrencia) HikariCP tiende a
     * devolver la MISMA conexión física una y otra vez, así que 15
     * peticiones seguidas cruzan de sobra el umbral de graduación a
     * "server-side prepared" de pgJDBC (5). Reproducido de forma real
     * contra este mismo endpoint antes de fijar la versión del driver:
     * las primeras 4 llamadas devolvían 200 y, desde la 5ª en adelante,
     * 400 con "Can't change resolved type for param: 3 from 2278 to 1790"
     * de forma consistente hasta reiniciar el proceso.
     */
    @Test
    void quinceLlamadasSeguidasAlEndpoint_todasDevuelven200ConLosMismosDatos() throws Exception {
        Usuario duenio = usuarioRepository.findByEmail(EMAIL_DUENO).orElseThrow();
        mascotaRepository.save(Mascota.builder()
                .duenio(duenio).nombre("Rex").especie("Perro").raza("Mestizo")
                .fechaNacimiento(LocalDate.of(2020, 1, 1)).activo(true).build());

        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        for (int i = 1; i <= 15; i++) {
            mockMvc.perform(get("/api/dashboard/resumen")
                            .param("desde", "2026-01-01")
                            .param("hasta", "2026-12-31")
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mascotasActivas").value(1));
        }
    }

    @Test
    void veterinarioPuedeConsultarResumen() throws Exception {
        String tokenVet = extractCookieValue(iniciarSesion(EMAIL_VET, PASSWORD_VET), "access_token");

        mockMvc.perform(get("/api/dashboard/resumen")
                        .param("desde", LocalDate.now().minusDays(7).toString())
                        .param("hasta", LocalDate.now().toString())
                        .header("Authorization", "Bearer " + tokenVet))
                .andExpect(status().isOk());
    }

    // ---------- autorizacion ----------

    @Test
    void duenoRecibe403() throws Exception {
        String tokenDueno = extractCookieValue(iniciarSesion(EMAIL_DUENO, PASSWORD_DUENO), "access_token");

        mockMvc.perform(get("/api/dashboard/resumen")
                        .param("desde", LocalDate.now().minusDays(7).toString())
                        .param("hasta", LocalDate.now().toString())
                        .header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinAutenticacionDevuelve401() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumen")
                        .param("desde", LocalDate.now().minusDays(7).toString())
                        .param("hasta", LocalDate.now().toString()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- validacion de rango ----------

    @Test
    void rangoInvertidoDevuelve400() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/dashboard/resumen")
                        .param("desde", LocalDate.now().toString())
                        .param("hasta", LocalDate.now().minusDays(10).toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:biopet:error:bad-request"));
    }

    @Test
    void parametrosFaltantesDevuelve400() throws Exception {
        String tokenAdmin = extractCookieValue(iniciarSesion(EMAIL_ADMIN, PASSWORD_ADMIN), "access_token");

        mockMvc.perform(get("/api/dashboard/resumen")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers (mismo patron que CitaControllerTest) ----------

    private MvcResult iniciarSesion(String email, String password) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
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
