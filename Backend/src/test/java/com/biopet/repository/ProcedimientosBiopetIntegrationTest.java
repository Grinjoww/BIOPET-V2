package com.biopet.repository;

import com.biopet.entity.Cita;
import com.biopet.entity.Consulta;
import com.biopet.entity.EstadoCita;
import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.entity.Vacuna;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F03: pruebas de integracion (PostgreSQL real via Testcontainers) de las 6
 * invocaciones formales de ProcedimientoBiopetRepository. Para cada SP/function:
 * caso feliz + caso de parametro invalido. Los privilegios minimos del rol
 * biopet_app se verifican en BiopetAppRolMinimoPrivilegiosIntegrationTest.
 */
@Testcontainers
@SpringBootTest
class ProcedimientosBiopetIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("biopet_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    static final List<String> ARCHIVOS_PROC = List.of(
            "fn_resumen_mascotas_por_especie.sql",
            "fn_historial_clinico_mascota.sql",
            "fn_reporte_dashboard.sql",
            "fn_siguiente_numero_ficha.sql",
            "sp_actualizar_estado_citas_masivas.sql",
            "sp_registrar_consulta_validada.sql");

    static String SQL_PROC;

    @BeforeAll
    static void leerProcFiles() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String archivo : ARCHIVOS_PROC) {
            sb.append(Files.readString(Paths.get("../db/procs/" + archivo))).append('\n');
        }
        SQL_PROC = sb.toString();
    }

    @BeforeEach
    void aplicarProcedimientos() {
        jdbcTemplate.execute(SQL_PROC);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    ProcedimientoBiopetRepository procedimientoBiopetRepository;
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

    private Usuario guardarUsuario(String email, Rol rol) {
        return usuarioRepository.save(Usuario.builder()
                .nombre("Usuario " + email)
                .email(email)
                .passwordHash("x")
                .rol(rol)
                .activo(true)
                .build());
    }

    private Mascota guardarMascota(Usuario duenio, String nombre) {
        return mascotaRepository.save(Mascota.builder()
                .duenio(duenio)
                .nombre(nombre)
                .especie("Perro")
                .raza("Mestizo")
                .fechaNacimiento(LocalDate.of(2020, 1, 1))
                .activo(true)
                .build());
    }

    @Test
    void resumenPorEspecie_duenioInexistente_devuelveListaVacia() {
        List<ResumenEspecie> resultado = procedimientoBiopetRepository.resumenPorEspecie(999999L);
        assertThat(resultado).isEmpty();
    }

    @Test
    void historialClinico_mascotaConDatos_consolidaHistorial() {
        Usuario duenio = guardarUsuario("duenio-historial@biopet.ec", Rol.ROLE_DUENO);
        Usuario vet = guardarUsuario("vet-historial@biopet.ec", Rol.ROLE_VETERINARIO);
        Mascota mascota = guardarMascota(duenio, "Rex");

        consultaRepository.save(Consulta.builder().mascota(mascota).veterinario(vet)
                .fechaConsulta(Instant.parse("2026-08-01T10:00:00Z"))
                .motivo("Chequeo").diagnostico("Sano").activo(true).build());
        vacunaRepository.save(Vacuna.builder().mascota(mascota).veterinario(vet).tipo("Rabia")
                .fechaAplicacion(LocalDate.of(2026, 7, 1))
                .proximaFecha(LocalDate.of(2027, 7, 1)).activo(true).build());
        citaRepository.save(Cita.builder().mascota(mascota).veterinario(vet)
                .fechaHora(Instant.parse("2026-09-01T10:00:00Z"))
                .estado(EstadoCita.PROGRAMADA).motivo("Control").activo(true).build());

        List<HistorialClinico> resultado = procedimientoBiopetRepository.historialClinicoMascota(mascota.getId());

        assertThat(resultado).hasSize(1);
        HistorialClinico h = resultado.get(0);
        assertThat(h.getMascota()).isEqualTo("Rex");
        assertThat(h.getEspecie()).isEqualTo("Perro");
        assertThat(h.getRaza()).isEqualTo("Mestizo");
        assertThat(h.getDuenio()).isEqualTo("Usuario duenio-historial@biopet.ec");
        assertThat(h.getNConsultas()).isEqualTo(1);
        assertThat(h.getUltimaConsulta()).isNotNull();
        assertThat(h.getUltimaVacuna()).isEqualTo("Rabia");
        assertThat(h.getProximaVacuna()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(h.getNCitas()).isEqualTo(1);
    }

    @Test
    void historialClinico_mascotaInexistente_devuelveListaVacia() {
        assertThat(procedimientoBiopetRepository.historialClinicoMascota(999999L)).isEmpty();
    }

    @Test
    void reporteDashboard_conDatos_calculaIndicadores() {
        Usuario duenio = guardarUsuario("duenio-reporte@biopet.ec", Rol.ROLE_DUENO);
        Usuario vet = guardarUsuario("vet-reporte@biopet.ec", Rol.ROLE_VETERINARIO);
        Mascota mascota = guardarMascota(duenio, "Rex");

        consultaRepository.save(Consulta.builder().mascota(mascota).veterinario(vet)
                .fechaConsulta(Instant.parse("2026-08-15T10:00:00Z"))
                .motivo("Chequeo").activo(true).build());
        vacunaRepository.save(Vacuna.builder().mascota(mascota).veterinario(vet).tipo("Rabia")
                .fechaAplicacion(LocalDate.of(2026, 8, 10)).activo(true).build());
        citaRepository.save(Cita.builder().mascota(mascota).veterinario(vet)
                .fechaHora(Instant.parse("2026-08-20T10:00:00Z"))
                .estado(EstadoCita.PROGRAMADA).activo(true).build());

        List<ReporteDashboard> resultado = procedimientoBiopetRepository.reporteDashboard(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(resultado).hasSize(1);
        ReporteDashboard r = resultado.get(0);
        assertThat(r.getMascotasActivas()).isEqualTo(1);
        assertThat(r.getCitasProgramadas()).isEqualTo(1);
        assertThat(r.getConsultasEnRango()).isEqualTo(1);
        assertThat(r.getVacunasEnRango()).isEqualTo(1);
        assertThat(r.getMascotasSinConsulta()).isZero();
    }

    @Test
    void reporteDashboard_rangoInvertido_noCuentaDentroDelRango() {
        Usuario duenio = guardarUsuario("duenio-rango@biopet.ec", Rol.ROLE_DUENO);
        guardarMascota(duenio, "Rex");

        List<ReporteDashboard> resultado = procedimientoBiopetRepository.reporteDashboard(
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1));

        assertThat(resultado).hasSize(1);
        ReporteDashboard r = resultado.get(0);
        assertThat(r.getMascotasActivas()).isEqualTo(1);
        assertThat(r.getConsultasEnRango()).isZero();
        assertThat(r.getVacunasEnRango()).isZero();
    }

    @Test
    void siguienteNumeroFicha_formatoYSecuencia() {
        String a = procedimientoBiopetRepository.siguienteNumeroFicha("FICHA");
        String b = procedimientoBiopetRepository.siguienteNumeroFicha("HIST");

        assertThat(a).matches("FICHA-\\d{6}");
        assertThat(b).matches("HIST-\\d{6}");
        assertThat(b).isNotEqualTo(a);
    }

    @Test
    void siguienteNumeroFicha_prefijoVacioUsaDefault() {
        String codigo = procedimientoBiopetRepository.siguienteNumeroFicha("");
        assertThat(codigo).matches("FICHA-\\d{6}");
    }

    @Test
    void actualizarEstadoCitasMasivas_soloActualizaLasQueCumplenFiltro() {
        Usuario duenio = guardarUsuario("duenio-citas@biopet.ec", Rol.ROLE_DUENO);
        Usuario vet = guardarUsuario("vet-citas@biopet.ec", Rol.ROLE_VETERINARIO);
        Mascota mascota = guardarMascota(duenio, "Rex");
        Instant limite = Instant.now();

        Cita pasadaProgramada = citaRepository.save(Cita.builder().mascota(mascota).veterinario(vet)
                .fechaHora(limite.minusSeconds(3600)).estado(EstadoCita.PROGRAMADA).activo(true).build());
        Cita futuraProgramada = citaRepository.save(Cita.builder().mascota(mascota).veterinario(vet)
                .fechaHora(limite.plusSeconds(3600)).estado(EstadoCita.PROGRAMADA).activo(true).build());
        Cita pasadaCompletada = citaRepository.save(Cita.builder().mascota(mascota).veterinario(vet)
                .fechaHora(limite.minusSeconds(7200)).estado(EstadoCita.COMPLETADA).activo(true).build());

        Long afectadas = procedimientoBiopetRepository.actualizarEstadoCitasMasivas(
                vet.getId(), "PROGRAMADA", "COMPLETADA", limite);

        assertThat(afectadas).isEqualTo(1);
        assertThat(citaRepository.findByIdAndActivoTrue(pasadaProgramada.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCita.COMPLETADA);
        assertThat(citaRepository.findByIdAndActivoTrue(futuraProgramada.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCita.PROGRAMADA);
        assertThat(citaRepository.findByIdAndActivoTrue(pasadaCompletada.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCita.COMPLETADA);
    }

    @Test
    void actualizarEstadoCitasMasivas_estadoInvalido_lanzaExcepcion() {
        assertThatThrownBy(() -> procedimientoBiopetRepository.actualizarEstadoCitasMasivas(
                1L, "PROGRAMADA", "ESTADO_INEXISTENTE", Instant.now()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void registrarConsultaValidada_casoFeliz_retornaIdYPersiste() {
        Usuario duenio = guardarUsuario("duenio-consulta@biopet.ec", Rol.ROLE_DUENO);
        Usuario vet = guardarUsuario("vet-consulta@biopet.ec", Rol.ROLE_VETERINARIO);
        Mascota mascota = guardarMascota(duenio, "Rex");

        Long consultaId = procedimientoBiopetRepository.registrarConsultaValidada(
                mascota.getId(), vet.getId(), "Chequeo anual", "Sano", "Ninguno", null);

        assertThat(consultaId).isNotNull();
        Consulta guardada = consultaRepository.findByIdAndActivoTrue(consultaId).orElseThrow();
        assertThat(guardada.getMotivo()).isEqualTo("Chequeo anual");
        assertThat(guardada.getDiagnostico()).isEqualTo("Sano");
    }

    @Test
    void registrarConsultaValidada_mascotaInexistente_lanzaExcepcion() {
        Usuario vet = guardarUsuario("vet-consulta-inexistente@biopet.ec", Rol.ROLE_VETERINARIO);

        assertThatThrownBy(() -> procedimientoBiopetRepository.registrarConsultaValidada(
                999999L, vet.getId(), "Chequeo", null, null, null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void registrarConsultaValidada_rolNoAutorizado_lanzaExcepcion() {
        Usuario duenio = guardarUsuario("duenio-consulta-noaut@biopet.ec", Rol.ROLE_DUENO);
        Mascota mascota = guardarMascota(duenio, "Rex");

        assertThatThrownBy(() -> procedimientoBiopetRepository.registrarConsultaValidada(
                mascota.getId(), duenio.getId(), "Chequeo", null, null, null))
                .isInstanceOf(DataAccessException.class);
    }
}
