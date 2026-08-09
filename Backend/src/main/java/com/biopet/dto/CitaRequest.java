package com.biopet.dto;

import com.biopet.entity.EstadoCita;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DTO de entrada para crear/actualizar una cita. El campo {@code estado} solo
 * tiene efecto en {@code PUT} (permite cancelar/completar); {@code POST} lo
 * ignora y fuerza siempre {@code PROGRAMADA}, igual que {@code RegistroRequest.rol()}
 * es ignorado por {@code AuthService.registrar()} y se fuerza a ROLE_DUENO.
 */
public record CitaRequest(
        @NotNull Long mascotaId,
        @NotNull Long veterinarioId,
        @NotNull Instant fechaHora,
        @NotNull EstadoCita estado,
        @Size(max = 255) String motivo
) {}
