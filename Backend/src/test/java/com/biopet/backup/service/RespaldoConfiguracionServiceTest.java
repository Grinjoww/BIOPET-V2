package com.biopet.backup.service;

import com.biopet.audit.service.AuditoriaService;
import com.biopet.backup.dto.RespaldoConfiguracionRequest;
import com.biopet.backup.dto.RespaldoConfiguracionResponse;
import com.biopet.backup.entity.RespaldoConfiguracion;
import com.biopet.backup.repository.RespaldoConfiguracionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Corrección del docente: la programación de respaldos ya no es un
 * intervalo (V9) sino "día de la semana + hora" (V10) -sin CRON visible
 * para el ADMIN. Estos tests fijan el reloj en instantes conocidos y
 * comprueban el cálculo de proximoRespaldoEn contra
 * America/Guayaquil -misma zona que usa el servicio-, cubriendo los tres
 * casos reales: el día configurado todavía no llegó esta semana, ya pasó
 * (la hora de hoy quedó atrás), y es exactamente ahora.
 */
class RespaldoConfiguracionServiceTest {

    private final Map<Long, RespaldoConfiguracion> filas = new HashMap<>();
    private RespaldoConfiguracionRepository repository;
    private AuditoriaService auditoriaService;
    private RespaldoConfiguracionService service;

    @BeforeEach
    void setUp() {
        filas.clear();
        repository = mock(RespaldoConfiguracionRepository.class);
        auditoriaService = mock(AuditoriaService.class);
        when(repository.findById(1L)).thenAnswer(inv -> Optional.ofNullable(filas.get(1L)));
        when(repository.save(any())).thenAnswer(inv -> {
            RespaldoConfiguracion c = inv.getArgument(0);
            filas.put(c.getId(), c);
            return c;
        });
    }

    private RespaldoConfiguracionService servicioConReloj(Instant ahora) {
        return new RespaldoConfiguracionService(repository, auditoriaService, Clock.fixed(ahora, ZoneOffset.UTC));
    }

    @Test
    void primeraLecturaCreaFilaPorDefectoInactivaLunesDosDeLaMadrugada() {
        service = servicioConReloj(Instant.parse("2026-09-08T12:00:00Z"));

        RespaldoConfiguracionResponse respuesta = service.obtener();

        assertThat(respuesta.activo()).isFalse();
        assertThat(respuesta.diaSemana()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(respuesta.hora()).isEqualTo(LocalTime.of(2, 0));
        assertThat(respuesta.ultimoRespaldoEn()).isNull();
        assertThat(respuesta.proximoRespaldoEn()).isNull();
        assertThat(respuesta.actualizadoPor()).isNull();
    }

    @Test
    void guardarActivandoConDiaFuturoEnLaMismaSemanaCalculaEseMismoDia() {
        // 2026-09-08 es martes. Configurar "viernes 15:00" debe caer esta
        // misma semana (viernes 11 de septiembre), no la siguiente.
        Instant ahora = Instant.parse("2026-09-08T12:00:00Z"); // martes 07:00 America/Guayaquil (UTC-5)
        service = servicioConReloj(ahora);

        RespaldoConfiguracionResponse respuesta = service.guardar(
                new RespaldoConfiguracionRequest(true, DayOfWeek.FRIDAY, LocalTime.of(15, 0)), "admin@biopet.ec");

        assertThat(respuesta.activo()).isTrue();
        assertThat(respuesta.diaSemana()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(respuesta.hora()).isEqualTo(LocalTime.of(15, 0));
        assertThat(respuesta.actualizadoEn()).isEqualTo(ahora);
        assertThat(respuesta.actualizadoPor()).isEqualTo("admin@biopet.ec");

        Instant esperado = Instant.parse("2026-09-11T20:00:00Z"); // viernes 15:00 UTC-5 = 20:00 UTC
        assertThat(respuesta.proximoRespaldoEn()).isEqualTo(esperado);
    }

    @Test
    void guardarConElDiaDeHoyPeroLaHoraYaPasadaSaltaALaSemanaSiguiente() {
        // 2026-09-08 es martes, 07:00 America/Guayaquil. Configurar "martes
        // 02:00" ya paso hoy: debe programarse para el martes SIGUIENTE.
        Instant ahora = Instant.parse("2026-09-08T12:00:00Z");
        service = servicioConReloj(ahora);

        RespaldoConfiguracionResponse respuesta = service.guardar(
                new RespaldoConfiguracionRequest(true, DayOfWeek.TUESDAY, LocalTime.of(2, 0)), "admin@biopet.ec");

        Instant esperado = Instant.parse("2026-09-15T07:00:00Z"); // martes siguiente, 02:00 UTC-5 = 07:00 UTC
        assertThat(respuesta.proximoRespaldoEn()).isEqualTo(esperado);
    }

    @Test
    void guardarConElDiaYHoraExactamenteAhoraSaltaALaSemanaSiguiente() {
        // Si "ahora" coincide EXACTO con dia+hora configurados, no dispara
        // de inmediato: se programa para la semana siguiente (evita un
        // disparo instantaneo justo al guardar).
        Instant ahora = Instant.parse("2026-09-08T12:00:00Z"); // martes 07:00 America/Guayaquil
        service = servicioConReloj(ahora);

        RespaldoConfiguracionResponse respuesta = service.guardar(
                new RespaldoConfiguracionRequest(true, DayOfWeek.TUESDAY, LocalTime.of(7, 0)), "admin@biopet.ec");

        Instant esperado = Instant.parse("2026-09-15T12:00:00Z");
        assertThat(respuesta.proximoRespaldoEn()).isEqualTo(esperado);
    }

    @Test
    void guardarDesactivandoLimpiaProximoRespaldo() {
        service = servicioConReloj(Instant.parse("2026-09-08T12:00:00Z"));
        service.guardar(new RespaldoConfiguracionRequest(true, DayOfWeek.MONDAY, LocalTime.of(2, 0)), "admin@biopet.ec");

        RespaldoConfiguracionResponse respuesta = service.guardar(
                new RespaldoConfiguracionRequest(false, DayOfWeek.MONDAY, LocalTime.of(2, 0)), "admin@biopet.ec");

        assertThat(respuesta.activo()).isFalse();
        assertThat(respuesta.proximoRespaldoEn()).isNull();
    }

    @Test
    void guardarNoTocaUltimoRespaldoEnAunNoHayEjecucion() {
        service = servicioConReloj(Instant.parse("2026-09-08T12:00:00Z"));

        RespaldoConfiguracionResponse respuesta = service.guardar(
                new RespaldoConfiguracionRequest(true, DayOfWeek.SUNDAY, LocalTime.of(3, 30)), "admin@biopet.ec");

        assertThat(respuesta.ultimoRespaldoEn()).isNull();
    }

    @Test
    void marcarEjecutadoActualizaUltimoYProximoSinTocarAuditoria() {
        Instant momentoGuardado = Instant.parse("2026-09-08T12:00:00Z");
        service = servicioConReloj(momentoGuardado);
        service.guardar(new RespaldoConfiguracionRequest(true, DayOfWeek.MONDAY, LocalTime.of(2, 0)), "admin@biopet.ec");

        Instant momentoEjecucion = Instant.parse("2026-09-14T07:00:00Z"); // el lunes programado, 02:00 UTC-5
        service.marcarEjecutado(momentoEjecucion);
        RespaldoConfiguracionResponse respuesta = service.obtener();

        assertThat(respuesta.ultimoRespaldoEn()).isEqualTo(momentoEjecucion);
        // Recalcula hacia el lunes SIGUIENTE (misma hora ya paso al recalcular desde el propio momento de ejecucion).
        assertThat(respuesta.proximoRespaldoEn()).isEqualTo(Instant.parse("2026-09-21T07:00:00Z"));
        // actualizadoEn/actualizadoPor siguen siendo los del guardado, no los de la ejecucion.
        assertThat(respuesta.actualizadoEn()).isEqualTo(momentoGuardado);
        assertThat(respuesta.actualizadoPor()).isEqualTo("admin@biopet.ec");
    }

    @Test
    void marcarEjecutadoConConfiguracionInactivaNoReprogramaProximo() {
        service = servicioConReloj(Instant.parse("2026-09-08T12:00:00Z"));
        service.guardar(new RespaldoConfiguracionRequest(false, DayOfWeek.MONDAY, LocalTime.of(2, 0)), "admin@biopet.ec");

        service.marcarEjecutado(Instant.parse("2026-09-08T12:00:00Z"));
        RespaldoConfiguracionResponse respuesta = service.obtener();

        assertThat(respuesta.proximoRespaldoEn()).isNull();
    }

    @Test
    void guardarEmiteEventoDeAuditoriaBackupConfigUpdated() {
        service = servicioConReloj(Instant.parse("2026-09-08T12:00:00Z"));

        service.guardar(new RespaldoConfiguracionRequest(true, DayOfWeek.MONDAY, LocalTime.of(2, 0)), "admin@biopet.ec");

        org.mockito.Mockito.verify(auditoriaService).registrar(
                org.mockito.ArgumentMatchers.eq("admin@biopet.ec"),
                org.mockito.ArgumentMatchers.eq("BACKUP_CONFIG_UPDATED"),
                org.mockito.ArgumentMatchers.eq("admin/respaldos"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("PUT"),
                org.mockito.ArgumentMatchers.eq("SUCCESS"),
                org.mockito.ArgumentMatchers.any());
    }
}
