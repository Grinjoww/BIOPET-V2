package com.biopet.dto;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

public record VacunaResponse(
        Long id,
        Long mascotaId,
        String mascotaNombre,
        Long veterinarioId,
        String veterinarioNombre,
        String tipo,
        LocalDate fechaAplicacion,
        LocalDate proximaFecha,
        String observaciones,
        boolean activo,
        Instant creadoEn,
        Instant actualizadoEn
) implements Serializable {}
