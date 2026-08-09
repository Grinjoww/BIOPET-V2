package com.biopet.dto;

import com.biopet.entity.EstadoCita;

import java.io.Serializable;
import java.time.Instant;

public record CitaResponse(
        Long id,
        Long mascotaId,
        String mascotaNombre,
        Long veterinarioId,
        String veterinarioNombre,
        Instant fechaHora,
        EstadoCita estado,
        String motivo,
        boolean activo,
        Instant creadoEn,
        Instant actualizadoEn
) implements Serializable {}
