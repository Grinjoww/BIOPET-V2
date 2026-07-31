package com.biopet.repository;

import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el trigger set_actualizado_en (definido en V1__schema_inicial.sql /
 * db/schema.sql) de forma aislada, es decir, SIN pasar por Hibernate.
 *
 * Nota: la entidad Mascota tambien tiene un @PreUpdate en Java que setea
 * actualizadoEn = Instant.now() antes de que Hibernate genere el UPDATE. Eso
 * hace que cualquier test que use mascotaRepository.save(...) para forzar un
 * UPDATE termine probando el @PreUpdate de Java, no el trigger de Postgres:
 * ambos escriben el mismo campo y el resultado final es indistinguible desde
 * afuera. Para aislar el trigger, este test ejecuta un UPDATE SQL crudo por
 * JdbcTemplate que solo toca la columna "nombre" (nunca "actualizado_en"), y
 * comprueba que "actualizado_en" cambio solo, por accion del trigger.
 */
@Testcontainers
@SpringBootTest
class TriggerActualizadoEnIntegrationTest {

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

    @Test
    void triggerActualizaActualizadoEnSinIntervencionDeHibernate() throws InterruptedException {
        Usuario duenio = usuarioRepository.save(Usuario.builder()
                .nombre("Test Trigger").email("test-trigger@biopet.ec")
                .passwordHash("x").rol(Rol.ROLE_DUENO).activo(true).build());

        Mascota mascota = mascotaRepository.save(Mascota.builder()
                .duenio(duenio).nombre("Rocky").especie("Perro")
                .raza("Mestizo").fechaNacimiento(LocalDate.of(2022, 5, 10)).activo(true).build());

        Instant actualizadoEnOriginal = jdbcTemplate.queryForObject(
                "SELECT actualizado_en FROM mascotas WHERE id = ?",
                Instant.class, mascota.getId());

        // Margen para que NOW() del trigger produzca un timestamp
        // distinguible del original (TIMESTAMPTZ tiene resolucion de
        // microsegundos, pero conviene no depender de eso).
        Thread.sleep(50);

        // UPDATE crudo por JDBC: solo toca "nombre", nunca "actualizado_en".
        // Si actualizado_en cambia igual, el cambio solo pudo venir del
        // trigger de Postgres, no de Hibernate (que aqui ni participa).
        int filasAfectadas = jdbcTemplate.update(
                "UPDATE mascotas SET nombre = ? WHERE id = ?",
                "Rocky (renombrado)", mascota.getId());

        Instant actualizadoEnDespuesDelTrigger = jdbcTemplate.queryForObject(
                "SELECT actualizado_en FROM mascotas WHERE id = ?",
                Instant.class, mascota.getId());

        assertThat(filasAfectadas).isEqualTo(1);
        assertThat(actualizadoEnDespuesDelTrigger).isAfter(actualizadoEnOriginal);
    }
}