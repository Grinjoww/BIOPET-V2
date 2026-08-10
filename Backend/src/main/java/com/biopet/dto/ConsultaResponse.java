package com.biopet.dto;

import java.io.Serializable;
import java.time.Instant;

public record ConsultaResponse(
        Long id,
        Long mascotaId,
        String mascotaNombre,
        Long veterinarioId,
        String veterinarioNombre,
        Instant fechaConsulta,
        String motivo,
        String diagnostico,
        String tratamiento,
        String observaciones,
        boolean activo,
        Instant creadoEn,
        Instant actualizadoEn
) implements Serializable {}