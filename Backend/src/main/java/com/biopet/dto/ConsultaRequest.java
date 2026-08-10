package com.biopet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ConsultaRequest(
        @NotNull Long mascotaId,
        @NotNull Long veterinarioId,
        @NotNull @PastOrPresent Instant fechaConsulta,
        @NotBlank @Size(max = 200) String motivo,
        @Size(max = 500) String diagnostico,
        @Size(max = 500) String tratamiento,
        @Size(max = 500) String observaciones
) {}