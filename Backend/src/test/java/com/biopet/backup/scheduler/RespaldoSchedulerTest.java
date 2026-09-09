package com.biopet.backup.scheduler;

import com.biopet.backup.dto.RespaldoConfiguracionResponse;
import com.biopet.backup.service.RespaldoConfiguracionService;
import com.biopet.backup.service.RespaldoEjecucionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RespaldoScheduler es la revision LIVIANA (ver su javadoc): estos tests
 * llaman a revisarYEjecutarSiCorresponde() directamente, sin esperar a
 * ningun @Scheduled real -lo que se prueba es la decision de negocio
 * (activo + proximoRespaldoEn vs "ahora"), no el mecanismo de Spring.
 */
class RespaldoSchedulerTest {

    private static final Instant AHORA = Instant.parse("2026-09-08T12:00:00Z");

    private RespaldoConfiguracionService configuracionService;
    private RespaldoEjecucionService ejecucionService;
    private RespaldoScheduler scheduler;

    @BeforeEach
    void setUp() {
        configuracionService = mock(RespaldoConfiguracionService.class);
        ejecucionService = mock(RespaldoEjecucionService.class);
        scheduler = new RespaldoScheduler(configuracionService, ejecucionService,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void schedulerApagadoNoEjecutaNada() {
        when(configuracionService.obtener()).thenReturn(configuracion(false, AHORA.minusSeconds(60)));

        scheduler.revisarYEjecutarSiCorresponde();

        verify(ejecucionService, never()).intentarEjecutarAutomatico();
    }

    @Test
    void schedulerActivoConProximoEnElPasadoEjecuta() {
        when(configuracionService.obtener()).thenReturn(configuracion(true, AHORA.minusSeconds(1)));

        scheduler.revisarYEjecutarSiCorresponde();

        verify(ejecucionService, times(1)).intentarEjecutarAutomatico();
    }

    @Test
    void schedulerActivoConProximoExactamenteAhoraEjecuta() {
        when(configuracionService.obtener()).thenReturn(configuracion(true, AHORA));

        scheduler.revisarYEjecutarSiCorresponde();

        verify(ejecucionService, times(1)).intentarEjecutarAutomatico();
    }

    @Test
    void schedulerActivoConProximoEnElFuturoNoEjecutaTodavia() {
        when(configuracionService.obtener()).thenReturn(configuracion(true, AHORA.plusSeconds(60)));

        scheduler.revisarYEjecutarSiCorresponde();

        verify(ejecucionService, never()).intentarEjecutarAutomatico();
    }

    @Test
    void schedulerActivoSinProximoRespaldoAunCalculadoEjecuta() {
        // Caso borde: activo=true pero proximoRespaldoEn todavia null (p.ej.
        // dato historico de antes de esta fase). No debe quedar nunca
        // "colgado" sin ejecutar jamas por falta de ese campo.
        when(configuracionService.obtener()).thenReturn(configuracion(true, null));

        scheduler.revisarYEjecutarSiCorresponde();

        verify(ejecucionService, times(1)).intentarEjecutarAutomatico();
    }

    @Test
    void unFalloAlConsultarLaConfiguracionNoPropagaLaExcepcion() {
        when(configuracionService.obtener()).thenThrow(new RuntimeException("fallo simulado de base de datos"));

        scheduler.revisarYEjecutarSiCorresponde(); // no debe lanzar

        verify(ejecucionService, never()).intentarEjecutarAutomatico();
    }

    @Test
    void unFalloAlDispararElRespaldoAutomaticoNoPropagaLaExcepcion() {
        when(configuracionService.obtener()).thenReturn(configuracion(true, AHORA.minusSeconds(1)));
        doThrowOnIntentarEjecutarAutomatico();

        scheduler.revisarYEjecutarSiCorresponde(); // no debe lanzar
    }

    private void doThrowOnIntentarEjecutarAutomatico() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo simulado de pg_dump"))
                .when(ejecucionService).intentarEjecutarAutomatico();
    }

    private RespaldoConfiguracionResponse configuracion(boolean activo, Instant proximoRespaldoEn) {
        return new RespaldoConfiguracionResponse(
                activo, DayOfWeek.MONDAY, LocalTime.of(2, 0), null, proximoRespaldoEn, AHORA, "admin@biopet.ec");
    }
}
