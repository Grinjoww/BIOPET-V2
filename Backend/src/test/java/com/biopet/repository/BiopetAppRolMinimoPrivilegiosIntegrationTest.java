package com.biopet.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F03: verificacion de que el rol biopet_app (cuenta de la aplicacion) tiene
 * los privilegios minimos necesarios para operar los 6 SP/funciones: ni de mas
 * (no puede ejecutar DDL, no ve objetos fuera de su permiso) ni de menos
 * (puede hacer CRUD sobre las tablas de dominio y ejecutar todas las rutinas).
 * El setup replica las intenciones de db/roles.sql adaptadas al esquema V1-V4.
 */
@Testcontainers
@SpringBootTest
class BiopetAppRolMinimoPrivilegiosIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("biopet_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    static final String APP_ROLE = "biopet_app";
    static final String APP_PASSWORD = "biopet_app_test_pass";

    static JdbcTemplate jdbcSuperusuario;
    static JdbcTemplate jdbcApp;

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

    @BeforeAll
    static void prepararBaseYRoles() throws IOException {
        jdbcSuperusuario = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();

        StringBuilder sb = new StringBuilder();
        for (String archivo : List.of(
                "fn_resumen_mascotas_por_especie.sql",
                "fn_historial_clinico_mascota.sql",
                "fn_reporte_dashboard.sql",
                "fn_siguiente_numero_ficha.sql",
                "sp_actualizar_estado_citas_masivas.sql",
                "sp_registrar_consulta_validada.sql")) {
            sb.append(Files.readString(Paths.get("../db/procs/" + archivo))).append('\n');
        }
        jdbcSuperusuario.execute(sb.toString());

        jdbcSuperusuario.execute("""
                CREATE ROLE biopet_app WITH LOGIN PASSWORD 'biopet_app_test_pass';
                GRANT USAGE ON SCHEMA public TO biopet_app;
                GRANT SELECT, INSERT, UPDATE, DELETE ON usuarios, mascotas, citas, consultas, vacunas TO biopet_app;
                GRANT USAGE, SELECT ON SEQUENCE usuarios_id_seq, mascotas_id_seq, citas_id_seq, consultas_id_seq, vacunas_id_seq TO biopet_app;
                GRANT EXECUTE ON FUNCTION set_actualizado_en() TO biopet_app;
                GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO biopet_app;
                GRANT EXECUTE ON ALL PROCEDURES IN SCHEMA public TO biopet_app;
                GRANT USAGE ON SEQUENCE seq_ficha_biopet TO biopet_app;
                """);

        jdbcApp = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), APP_ROLE, APP_PASSWORD));
    }

    @Test
    void biopet_app_ejecutaLasSeisFuncionesYProcedimientos() {
        assertThat(jdbcApp.queryForList("SELECT * FROM fn_resumen_mascotas_por_especie(NULL)")).isEmpty();
        assertThat(jdbcApp.queryForList("SELECT * FROM fn_historial_clinico_mascota(999999)")).isEmpty();
        assertThat(jdbcApp.queryForList("SELECT * FROM fn_reporte_dashboard('2026-01-01', '2026-12-31')"))
                .hasSize(1);

        String codigo = jdbcApp.queryForObject("SELECT p_codigo FROM fn_siguiente_numero_ficha('ROL')", String.class);
        assertThat(codigo).matches("ROL-\\d{6}");

        try (Connection con = jdbcApp.getDataSource().getConnection();
             CallableStatement cs = con.prepareCall("CALL sp_actualizar_estado_citas_masivas(?, ?, ?, ?, ?)")) {
            cs.setLong(1, 1L);
            cs.setString(2, "PROGRAMADA");
            cs.setString(3, "COMPLETADA");
            cs.setTimestamp(4, Timestamp.from(Instant.now()));
            cs.registerOutParameter(5, Types.BIGINT);
            cs.execute();
            assertThat(cs.getLong(5)).isZero();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void biopet_app_haceCrudYUsaProcedimientoConValidacion() {
        jdbcApp.update("INSERT INTO usuarios (nombre, email, password_hash, rol, activo) " +
                "VALUES ('Vet App', 'vet-app@biopet.ec', 'x', 'ROLE_VETERINARIO', TRUE)");
        Long vetId = jdbcApp.queryForObject("SELECT id FROM usuarios WHERE email = 'vet-app@biopet.ec'", Long.class);
        jdbcApp.update("INSERT INTO usuarios (nombre, email, password_hash, rol, activo) " +
                "VALUES ('Dueno App', 'dueno-app@biopet.ec', 'x', 'ROLE_DUENO', TRUE)");
        Long duenioId = jdbcApp.queryForObject("SELECT id FROM usuarios WHERE email = 'dueno-app@biopet.ec'", Long.class);
        jdbcApp.update("INSERT INTO mascotas (duenio_id, nombre, especie, raza, fecha_nacimiento, activo) " +
                "VALUES (?, 'Rex', 'Perro', 'Labrador', ?, TRUE)",
                duenioId, Date.valueOf(LocalDate.of(2020, 1, 1)));
        Long mascotaId = jdbcApp.queryForObject("SELECT id FROM mascotas WHERE nombre = 'Rex'", Long.class);

        try (Connection con = jdbcApp.getDataSource().getConnection();
             CallableStatement cs = con.prepareCall("CALL sp_registrar_consulta_validada(?, ?, ?, ?, ?, ?, ?)")) {
            cs.setLong(1, mascotaId);
            cs.setLong(2, vetId);
            cs.setString(3, "Chequeo");
            cs.setNull(4, Types.VARCHAR);
            cs.setNull(5, Types.VARCHAR);
            cs.setNull(6, Types.VARCHAR);
            cs.registerOutParameter(7, Types.BIGINT);
            cs.execute();
            assertThat(cs.getLong(7)).isPositive();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        assertThat(jdbcApp.queryForObject("SELECT COUNT(*) FROM consultas", Integer.class)).isEqualTo(1);

        jdbcApp.update("UPDATE mascotas SET nombre = 'Rex Actualizado' WHERE id = ?", mascotaId);
        jdbcApp.update("DELETE FROM vacunas WHERE id = -1");
    }

    @Test
    void biopet_app_noPuedeEjecutarDDL() {
        assertThatThrownBy(() -> jdbcApp.execute("CREATE TABLE tabla_prohibida (id BIGINT)"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcApp.execute("DROP TABLE usuarios"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcApp.execute("ALTER TABLE usuarios ADD COLUMN extra VARCHAR(10)"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcApp.execute("TRUNCATE TABLE usuarios"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcApp.execute("CREATE SEQUENCE seq_prohibida"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcApp.execute("COMMENT ON TABLE usuarios IS 'prohibido'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcApp.execute("ALTER TABLE usuarios OWNER TO biopet_app"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void biopet_app_noVeObjetosFueraDeSuPermiso() {
        assertThatThrownBy(() -> jdbcApp.queryForList("SELECT * FROM flyway_schema_history"))
                .isInstanceOf(DataAccessException.class);
    }
}
