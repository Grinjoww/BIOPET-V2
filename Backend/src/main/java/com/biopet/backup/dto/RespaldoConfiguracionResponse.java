package com.biopet.backup.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

public record RespaldoConfiguracionResponse(
        boolean activo,
        DayOfWeek diaSemana,
        LocalTime hora,
        Instant ultimoRespaldoEn,
        Instant proximoRespaldoEn,
        Instant actualizadoEn,
        String actualizadoPor
) {}
