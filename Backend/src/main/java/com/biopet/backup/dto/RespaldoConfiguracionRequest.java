package com.biopet.backup.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Cuerpo de PUT /api/admin/respaldos/configuracion. Programacion semanal
 * simple ("todos los diaSemana a las hora"), sin CRON: el docente pidio
 * explicitamente que el ADMIN nunca vea una expresion CRON.
 */
public record RespaldoConfiguracionRequest(
        boolean activo,
        @NotNull DayOfWeek diaSemana,
        @NotNull LocalTime hora
) {}
