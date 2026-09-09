package com.biopet;

import com.biopet.backup.entity.EstadoRespaldo;
import com.biopet.backup.entity.RespaldoHistorial;
import com.biopet.backup.pgdump.PgDumpExecutor;
import com.biopet.backup.repository.RespaldoConfiguracionRepository;
import com.biopet.backup.repository.RespaldoHistorialRepository;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.UsuarioRepository;
import com.biopet.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/admin/respaldos bajo el perfil "test" normal (H2, Flyway
 * deshabilitado -ver application-test.yml): las dos tablas del modulo
 * (respaldo_configuracion, respaldo_historial) las crea Hibernate desde las
 * entidades (ddl-auto=create-drop), igual que el resto de *ControllerTest
 * de este paquete.
 *
 * <p>PgDumpExecutor SIEMPRE es un @MockBean -nunca se lanza un pg_dump
 * real en esta clase- y backup.pgdump-source-url/usuario/password se
 * sobrescriben a un valor con forma de URL de PostgreSQL (nunca se conecta
 * de verdad: el mock no abre ningun socket) para desacoplar el respaldo del
 * datasource H2 real de este perfil, sin tocarlo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RespaldoControllerTest {

    @TempDir
    static Path directorioRespaldos;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("backup.storage-path", () -> directorioRespaldos.toString());
        registry.add("backup.pgdump-source-url", () -> "jdbc:postgresql://localhost:5432/biopet_test_fake");
        registry.add("backup.pgdump-usuario", () -> "biopet_user");
        registry.add("backup.pgdump-password", () -> "no-se-usa-el-mock-nunca-conecta");
    }

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RespaldoConfiguracionRepository configuracionRepository;
    @Autowired RespaldoHistorialRepository historialRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean PgDumpExecutor pgDumpExecutor;

    @BeforeEach
    void setUp() {
        historialRepository.deleteAll();
        configuracionRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario admin = Usuario.builder()
                .nombre("Admin Respaldos")
                .email("admin.respaldos@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build();
        usuarioRepository.save(admin);

        Usuario dueno = Usuario.builder()
                .nombre("Dueño Cualquiera")
                .email("dueno.respaldos@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveDueno123*"))
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build();
        usuarioRepository.save(dueno);

        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);

        // Por defecto, un pg_dump "exitoso": escribe un archivo pequeño en
        // la ruta pedida. Cada test que necesite otro comportamiento lo
        // reconfigura explicitamente.
        doAnswer(inv -> {
            com.biopet.backup.pgdump.PgDumpParametros p = inv.getArgument(0);
            Files.writeString(p.rutaSalida(), "contenido de prueba");
            return null;
        }).when(pgDumpExecutor).ejecutar(any());
    }

    @Test
    void adminConsultaConfiguracionPorDefecto() throws Exception {
        String tokenAdmin = tokenDe("admin.respaldos@biopet.com", "ClaveCorrecta123*");

        mockMvc.perform(get("/api/admin/respaldos/configuracion").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.diaSemana").value("MONDAY"))
                .andExpect(jsonPath("$.hora").value("02:00:00"))
                .andExpect(jsonPath("$.ultimoRespaldoEn").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void noAdminRecibe403AlConsultarConfiguracion() throws Exception {
        String tokenDueno = tokenDe("dueno.respaldos@biopet.com", "ClaveDueno123*");

        mockMvc.perform(get("/api/admin/respaldos/configuracion").header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:forbidden"));
    }

    @Test
    void noAdminRecibe403AlIntentarGenerarRespaldo() throws Exception {
        String tokenDueno = tokenDe("dueno.respaldos@biopet.com", "ClaveDueno123*");

        mockMvc.perform(post("/api/admin/respaldos/ejecutar").header("Authorization", "Bearer " + tokenDueno))
                .andExpect(status().isForbidden());
    }

    @Test
    void activarYGuardarDiaYHoraPersiste() throws Exception {
        String tokenAdmin = tokenDe("admin.respaldos@biopet.com", "ClaveCorrecta123*");

        mockMvc.perform(put("/api/admin/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"activo":true,"diaSemana":"FRIDAY","hora":"15:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.diaSemana").value("FRIDAY"))
                .andExpect(jsonPath("$.hora").value("15:00:00"))
                .andExpect(jsonPath("$.actualizadoPor").value("admin.respaldos@biopet.com"))
                .andExpect(jsonPath("$.proximoRespaldoEn").exists());

        // Persiste: una nueva consulta (nueva peticion HTTP) ve lo mismo.
        mockMvc.perform(get("/api/admin/respaldos/configuracion").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.diaSemana").value("FRIDAY"))
                .andExpect(jsonPath("$.hora").value("15:00:00"));
    }

    @Test
    void desactivarLimpiaProximoRespaldo() throws Exception {
        String tokenAdmin = tokenDe("admin.respaldos@biopet.com", "ClaveCorrecta123*");
        guardarConfiguracion(tokenAdmin, true, "MONDAY", "02:00");

        mockMvc.perform(put("/api/admin/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"activo":false,"diaSemana":"MONDAY","hora":"02:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.proximoRespaldoEn").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void diaSemanaAusenteEsRechazado() throws Exception {
        String tokenAdmin = tokenDe("admin.respaldos@biopet.com", "ClaveCorrecta123*");

        mockMvc.perform(put("/api/admin/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"activo":true,"hora":"02:00"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:validation"))
                .andExpect(jsonPath("$.errors.diaSemana").isArray());
    }

    @Test
    void horaConFormatoInvalidoEsRechazada() throws Exception {
        String tokenAdmin = tokenDe("admin.respaldos@biopet.com", "ClaveCorrecta123*");

        // "25:99" no es una hora valida: falla al deserializar (nunca llega
        // a la validacion de Bean Validation), asi que el docente nunca ve
        // una hora imposible aceptada.
        mockMvc.perform(put("/api/admin/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"activo":true,"diaSemana":"MONDAY","hora":"25:99"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualGeneraFilaDeHistorialVisibleEnElListadoPaginado() throws Exception {
        String tokenAdmin = tokenDe("admin.respaldos@biopet.com", "ClaveCorrecta123*");

        // La respuesta sincrona del 202 SIEMPRE muestra EN_PROCESO: se
        // construye a partir del objeto en memoria capturado antes de
        // someter el trabajo real al executor de fondo, sin importar cuan
        // rapido termine este despues (ver RespaldoEjecucionService.ejecutarManual).
        mockMvc.perform(post("/api/admin/respaldos/ejecutar").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tipo").value("MANUAL"))
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.ejecutadoPor").value("admin.respaldos@biopet.com"));

        esperarHasta(() -> historialRepository.count() == 1
                && historialRepository.findAll().get(0).getEstado() != EstadoRespaldo.EN_PROCESO);

        List<RespaldoHistorial> filas = historialRepository.findAll();
        org.assertj.core.api.Assertions.assertThat(filas).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(filas.get(0).getEstado()).isEqualTo(EstadoRespaldo.EXITOSO);

        mockMvc.perform(get("/api/admin/respaldos/historial").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("MANUAL"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void fallosDePgDumpQuedanComoFallidoEnElHistorial() throws Exception {
        doAnswer(inv -> {
            throw new com.biopet.backup.pgdump.PgDumpExecutionException(
                    com.biopet.backup.pgdump.TipoFalloPgDump.CODIGO_SALIDA, "pg_dump finalizo con codigo 1.");
        }).when(pgDumpExecutor).ejecutar(any());

        String tokenAdmin = tokenDe("admin.respaldos@biopet.com", "ClaveCorrecta123*");
        mockMvc.perform(post("/api/admin/respaldos/ejecutar").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isAccepted());

        esperarHasta(() -> historialRepository.count() == 1
                && historialRepository.findAll().get(0).getEstado() != EstadoRespaldo.EN_PROCESO);

        RespaldoHistorial fila = historialRepository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(fila.getEstado()).isEqualTo(EstadoRespaldo.FALLIDO);
        org.assertj.core.api.Assertions.assertThat(fila.getMensajeErrorSeguro())
                .isEqualTo("pg_dump finalizo con codigo 1.");
    }

    @Test
    void segundoRespaldoManualEsRechazadoMientrasElPrimeroEstaEnProceso() throws Exception {
        CountDownLatch dumpIniciado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        doAnswer(inv -> {
            dumpIniciado.countDown();
            if (!liberar.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("El test no libero el latch a tiempo");
            }
            return null;
        }).when(pgDumpExecutor).ejecutar(any());

        String tokenAdmin = tokenDe("admin.respaldos@biopet.com", "ClaveCorrecta123*");

        mockMvc.perform(post("/api/admin/respaldos/ejecutar").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isAccepted());
        org.assertj.core.api.Assertions.assertThat(dumpIniciado.await(2, TimeUnit.SECONDS)).isTrue();

        mockMvc.perform(post("/api/admin/respaldos/ejecutar").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:biopet:error:conflict"));

        liberar.countDown();
        esperarHasta(() -> historialRepository.findAll().stream()
                .noneMatch(h -> h.getEstado() == EstadoRespaldo.EN_PROCESO));
    }

    // ---------- Helpers ----------

    private void guardarConfiguracion(String tokenAdmin, boolean activo, String diaSemana, String hora) throws Exception {
        mockMvc.perform(put("/api/admin/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"activo":%s,"diaSemana":"%s","hora":"%s"}
                                """.formatted(activo, diaSemana, hora)))
                .andExpect(status().isOk());
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

    private void esperarHasta(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 3000;
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() > limite) {
                throw new AssertionError("La condicion esperada no se cumplio a tiempo");
            }
            Thread.sleep(20);
        }
    }
}
