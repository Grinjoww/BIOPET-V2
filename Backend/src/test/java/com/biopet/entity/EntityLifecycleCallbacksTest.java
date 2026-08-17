package com.biopet.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura de rama de los callbacks @PrePersist de las entidades de dominio:
 * cada entidad decide, con un `if (campo == null) campo = valor`, si debe
 * autocompletar timestamps (y en Cita/Usuario, también un valor por defecto
 * de estado/rol) o respetar un valor ya asignado explícitamente. Ambas ramas
 * (campo nulo / campo ya asignado) se ejercitan aquí para cada entidad,
 * invocando directamente el método de paquete `prePersist()` (visible desde
 * esta clase de prueba porque vive en el mismo paquete `com.biopet.entity`).
 */
class EntityLifecycleCallbacksTest {

    @Test
    void citaPrePersistAutocompletaCuandoCamposSonNulos() {
        Cita cita = new Cita();

        cita.prePersist();

        assertNotNull(cita.getCreadoEn());
        assertNotNull(cita.getActualizadoEn());
        assertTrue(cita.isActivo());
        assertEquals(EstadoCita.PROGRAMADA, cita.getEstado());
    }

    @Test
    void citaPrePersistRespetaValoresYaAsignados() {
        Instant creado = Instant.parse("2026-01-01T00:00:00Z");
        Instant actualizado = Instant.parse("2026-01-02T00:00:00Z");
        Cita cita = new Cita();
        cita.setCreadoEn(creado);
        cita.setActualizadoEn(actualizado);
        cita.setEstado(EstadoCita.COMPLETADA);

        cita.prePersist();

        assertEquals(creado, cita.getCreadoEn());
        assertEquals(actualizado, cita.getActualizadoEn());
        assertEquals(EstadoCita.COMPLETADA, cita.getEstado());
    }

    @Test
    void usuarioPrePersistAutocompletaCuandoCamposSonNulos() {
        Usuario usuario = new Usuario();

        usuario.prePersist();

        assertNotNull(usuario.getCreadoEn());
        assertNotNull(usuario.getActualizadoEn());
        assertTrue(usuario.isActivo());
        assertEquals(Rol.ROLE_DUENO, usuario.getRol());
    }

    @Test
    void usuarioPrePersistRespetaValoresYaAsignados() {
        Instant creado = Instant.parse("2026-01-01T00:00:00Z");
        Instant actualizado = Instant.parse("2026-01-02T00:00:00Z");
        Usuario usuario = new Usuario();
        usuario.setCreadoEn(creado);
        usuario.setActualizadoEn(actualizado);
        usuario.setRol(Rol.ROLE_ADMIN);

        usuario.prePersist();

        assertEquals(creado, usuario.getCreadoEn());
        assertEquals(actualizado, usuario.getActualizadoEn());
        assertEquals(Rol.ROLE_ADMIN, usuario.getRol());
    }

    @Test
    void consultaPrePersistAutocompletaCuandoCamposSonNulos() {
        Consulta consulta = new Consulta();

        consulta.prePersist();

        assertNotNull(consulta.getCreadoEn());
        assertNotNull(consulta.getActualizadoEn());
        assertTrue(consulta.isActivo());
    }

    @Test
    void consultaPrePersistRespetaValoresYaAsignados() {
        Instant creado = Instant.parse("2026-01-01T00:00:00Z");
        Instant actualizado = Instant.parse("2026-01-02T00:00:00Z");
        Consulta consulta = new Consulta();
        consulta.setCreadoEn(creado);
        consulta.setActualizadoEn(actualizado);

        consulta.prePersist();

        assertEquals(creado, consulta.getCreadoEn());
        assertEquals(actualizado, consulta.getActualizadoEn());
    }

    @Test
    void mascotaPrePersistAutocompletaCuandoCamposSonNulos() {
        Mascota mascota = new Mascota();

        mascota.prePersist();

        assertNotNull(mascota.getCreadoEn());
        assertNotNull(mascota.getActualizadoEn());
        assertTrue(mascota.isActivo());
    }

    @Test
    void mascotaPrePersistRespetaValoresYaAsignados() {
        Instant creado = Instant.parse("2026-01-01T00:00:00Z");
        Instant actualizado = Instant.parse("2026-01-02T00:00:00Z");
        Mascota mascota = new Mascota();
        mascota.setCreadoEn(creado);
        mascota.setActualizadoEn(actualizado);

        mascota.prePersist();

        assertEquals(creado, mascota.getCreadoEn());
        assertEquals(actualizado, mascota.getActualizadoEn());
    }

    @Test
    void vacunaPrePersistAutocompletaCuandoCamposSonNulos() {
        Vacuna vacuna = new Vacuna();

        vacuna.prePersist();

        assertNotNull(vacuna.getCreadoEn());
        assertNotNull(vacuna.getActualizadoEn());
        assertTrue(vacuna.isActivo());
    }

    @Test
    void vacunaPrePersistRespetaValoresYaAsignados() {
        Instant creado = Instant.parse("2026-01-01T00:00:00Z");
        Instant actualizado = Instant.parse("2026-01-02T00:00:00Z");
        Vacuna vacuna = new Vacuna();
        vacuna.setCreadoEn(creado);
        vacuna.setActualizadoEn(actualizado);

        vacuna.prePersist();

        assertEquals(creado, vacuna.getCreadoEn());
        assertEquals(actualizado, vacuna.getActualizadoEn());
    }

    @Test
    void citaPreUpdateActualizaMarcaDeTiempo() {
        Cita cita = new Cita();
        cita.setActualizadoEn(Instant.parse("2020-01-01T00:00:00Z"));

        cita.preUpdate();

        assertTrue(cita.getActualizadoEn().isAfter(Instant.parse("2020-01-01T00:00:00Z")));
    }
}
