package com.biopet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VacunaRequest(
        @NotNull Long mascotaId,
        Long veterinarioId,
        @NotBlank @Size(max = 60) String tipo,
        @NotNull @PastOrPresent LocalDate fechaAplicacion,
        LocalDate proximaFecha,
        @Size(max = 255) String observaciones
) {}
