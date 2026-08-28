package com.biopet.facturacion.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demuestra que V7 y V8 se pueden aplicar sobre una base que YA esta en
 * produccion con datos, sin tocar ni un solo registro previo.
 *
 * <p>Es el escenario real del despliegue: BIOPET ya funciona, sus tablas tienen
 * usuarios, mascotas, citas, consultas y vacunas, y el modulo de facturacion
 * llega despues. Por eso el test no arranca Spring: usa la API de Flyway
 * directamente para poder detenerse en V6, meter datos, y solo entonces
 * continuar hasta V8. Con un contexto de Spring normal las ocho migraciones
 * correrian de golpe y no habria forma de intercalar los datos.
 *
 * <p>Se comprueban tres cosas distintas, y las tres importan:
 * <ol>
 *   <li>las filas siguen existiendo (nada se borro);</li>
 *   <li>su contenido es identico campo a campo (nada se reescribio);</li>
 *   <li>{@code actualizado_en} NO cambio. Esto ultimo es la comprobacion mas
 *       fina: los triggers de auditoria que V7/V8 anaden se disparan en cada
 *       UPDATE, asi que si alguna migracion hubiese tocado una fila previa
 *       -aunque fuese para dejarla igual- la marca de tiempo lo delataria.</li>
 * </ol>
 */
@Testcontainers
class FacturacionCompatibilidadDatosPreviosIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("biopet_compat_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    private static final List<String> TABLAS_PREVIAS =
            List.of("usuarios", "mascotas", "citas", "consultas", "vacunas");

    @Test
    void v7yV8seAplicanSobreUnaBaseConDatosSinAlterarlos() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

        // ---------- 1. Esquema tal y como esta hoy en produccion: V1 -> V6 ----------
        migrarHasta("6");

        assertThat(versionesAplicadas(jdbc)).containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(tablasExistentes(jdbc)).doesNotContain("facturas", "emisor_fiscal");

        // ---------- 2. Datos de un BIOPET ya en marcha ----------
        jdbc.update("INSERT INTO usuarios (nombre, email, password_hash, rol, activo) "
                + "VALUES ('Vet Previo', 'vet-previo@biopet.test', 'hash-previo', 'ROLE_VETERINARIO', TRUE)");
        jdbc.update("INSERT INTO usuarios (nombre, email, password_hash, rol, activo) "
                + "VALUES ('Dueno Previo', 'dueno-previo@biopet.test', 'hash-previo', 'ROLE_DUENO', TRUE)");
        Long vetId = jdbc.queryForObject(
                "SELECT id FROM usuarios WHERE email = 'vet-previo@biopet.test'", Long.class);
        Long duenioId = jdbc.queryForObject(
                "SELECT id FROM usuarios WHERE email = 'dueno-previo@biopet.test'", Long.class);

        jdbc.update("INSERT INTO mascotas (duenio_id, nombre, especie, raza, fecha_nacimiento, activo) "
                        + "VALUES (?, 'Michi', 'Gato', 'Siames', ?, TRUE)",
                duenioId, Date.valueOf(LocalDate.of(2019, 8, 2)));
        Long mascotaId = jdbc.queryForObject(
                "SELECT id FROM mascotas WHERE nombre = 'Michi'", Long.class);

        jdbc.update("INSERT INTO citas (mascota_id, veterinario_id, fecha_hora, estado, motivo, activo) "
                + "VALUES (?, ?, NOW(), 'PROGRAMADA', 'Control previo', TRUE)", mascotaId, vetId);
        jdbc.update("INSERT INTO consultas (mascota_id, veterinario_id, fecha_consulta, motivo, "
                + "diagnostico, activo) VALUES (?, ?, NOW(), 'Motivo previo', 'Diagnostico previo', TRUE)",
                mascotaId, vetId);
        jdbc.update("INSERT INTO vacunas (mascota_id, veterinario_id, tipo, fecha_aplicacion, activo) "
                        + "VALUES (?, ?, 'Triple felina', ?, TRUE)",
                mascotaId, vetId, Date.valueOf(LocalDate.of(2026, 1, 15)));

        // Fotografia completa de cada tabla ANTES de migrar.
        Map<String, List<Map<String, Object>>> antes = fotografiar(jdbc);
        antes.forEach((tabla, filas) ->
                assertThat(filas).as("fixture de %s", tabla).isNotEmpty());

        // ---------- 3. Llega el modulo de facturacion: V7 -> V8 ----------
        migrarHasta("8");

        assertThat(versionesAplicadas(jdbc))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(tablasExistentes(jdbc)).contains(
                "emisor_fiscal", "punto_emision", "secuencial_emision", "tarifa_impuesto",
                "concepto_facturable", "datos_facturacion", "facturas", "factura_detalles",
                "factura_pagos", "factura_documentos", "factura_eventos_sri");

        // ---------- 4. Nada de lo anterior cambio ----------
        Map<String, List<Map<String, Object>>> despues = fotografiar(jdbc);

        for (String tabla : TABLAS_PREVIAS) {
            assertThat(despues.get(tabla))
                    .as("las filas de %s deben sobrevivir intactas a V7/V8", tabla)
                    .isEqualTo(antes.get(tabla));
        }

        // Y las tablas nuevas nacen vacias sobre una base con datos: la
        // migracion no inventa un emisor ni facturas para los datos previos.
        for (String tabla : List.of("emisor_fiscal", "punto_emision", "secuencial_emision",
                "tarifa_impuesto", "concepto_facturable", "datos_facturacion", "facturas")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class))
                    .as("%s debe nacer vacia", tabla)
                    .isZero();
        }

        // ---------- 5. Ni una columna de mas ni de menos en lo anterior ----------
        // Se comprueba explicitamente porque un ALTER TABLE colado en una
        // migracion "aditiva" es un error facil de cometer y dificil de ver:
        // no borraria datos, pero rompe la promesa de que V7/V8 no tocan V1-V4.
        assertThat(columnasDe(jdbc, "usuarios")).containsExactly(
                "activo", "actualizado_en", "creado_en", "email", "id", "nombre",
                "password_hash", "rol");
        assertThat(columnasDe(jdbc, "mascotas")).containsExactly(
                "activo", "actualizado_en", "creado_en", "duenio_id", "especie",
                "fecha_nacimiento", "id", "nombre", "raza");
        assertThat(columnasDe(jdbc, "citas")).containsExactly(
                "activo", "actualizado_en", "creado_en", "estado", "fecha_hora", "id",
                "mascota_id", "motivo", "veterinario_id");
        assertThat(columnasDe(jdbc, "consultas")).containsExactly(
                "activo", "actualizado_en", "creado_en", "diagnostico", "fecha_consulta",
                "id", "mascota_id", "motivo", "observaciones", "tratamiento", "veterinario_id");
        assertThat(columnasDe(jdbc, "vacunas")).containsExactly(
                "activo", "actualizado_en", "creado_en", "fecha_aplicacion", "id",
                "mascota_id", "observaciones", "proxima_fecha", "tipo", "veterinario_id");
    }

    // ------------------------------------------------------------------

    private void migrarHasta(String version) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private List<String> versionesAplicadas(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL "
                        + "AND success = TRUE ORDER BY installed_rank", String.class);
    }

    private List<String> tablasExistentes(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
    }

    private List<String> columnasDe(JdbcTemplate jdbc, String tabla) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? ORDER BY column_name",
                String.class, tabla);
    }

    /** Todas las filas de las tablas previas, ordenadas, con todas sus columnas. */
    private Map<String, List<Map<String, Object>>> fotografiar(JdbcTemplate jdbc) {
        return TABLAS_PREVIAS.stream().collect(java.util.stream.Collectors.toMap(
                tabla -> tabla,
                tabla -> jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id")));
    }
}
