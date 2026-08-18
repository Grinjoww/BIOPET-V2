package com.biopet.repository;

import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F02/F03: fn_resumen_mascotas_por_especie es ahora PROCEDURE con OUT
 * refcursor (ver ProcedimientoBiopetRepository); el cursor solo es legible
 * dentro de la misma transaccion en la que se abre, por eso esta clase
 * lleva @Transactional.
 */
@Testcontainers
@SpringBootTest
@Transactional
class ResumenEspeciesIntegrationTest {

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

    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    MascotaRepository mascotaRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    ProcedimientoBiopetRepository procedimientoBiopetRepository;

    @BeforeAll
    static void aplicarFuncion() throws IOException {
        String sql = Files.readString(Paths.get("../db/procs/fn_resumen_mascotas_por_especie.sql"));
        // se aplica dentro del test una vez que Flyway ya corrio (ver setUp abajo)
        FUNCION_SQL = sql;
    }

    static String FUNCION_SQL;

    @Test
    void resumenAgrupaPorEspecieYFiltraPorDuenio() {
        jdbcTemplate.execute(FUNCION_SQL);

        Usuario duenio = usuarioRepository.save(Usuario.builder()
                .nombre("Test Duenio").email("test-duenio@biopet.ec")
                .passwordHash("x").rol(Rol.ROLE_DUENO).activo(true).build());

        mascotaRepository.save(Mascota.builder()
        .duenio(duenio).nombre("Firulais").especie("Perro")
        .raza("Mestizo").fechaNacimiento(LocalDate.of(2020,1,1)).activo(true).build());
        mascotaRepository.save(Mascota.builder()
        .duenio(duenio).nombre("Michi").especie("Gato")
        .raza("Mestizo").fechaNacimiento(LocalDate.of(2021,1,1)).activo(true).build());
        List<ResumenEspecie> resultado = procedimientoBiopetRepository.resumenPorEspecie(duenio.getId());

        assertThat(resultado).hasSize(2);
        assertThat(resultado).anyMatch(r -> r.getEspecie().equals("Perro") && r.getTotal() == 1);
        assertThat(resultado).anyMatch(r -> r.getEspecie().equals("Gato") && r.getTotal() == 1);
    }
}