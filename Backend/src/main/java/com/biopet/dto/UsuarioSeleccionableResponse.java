package com.biopet.dto;

import com.biopet.entity.Rol;

/**
 * DTO de solo lectura para poblar selectores del frontend (p. ej. "dueño" al
 * crear una mascota, "veterinario" al crear una cita/consulta/vacuna).
 * <p>
 * Deliberadamente distinto de {@code UsuarioResponse}: no expone {@code activo}
 * (siempre true por construcción, ver {@code UsuarioService.listarDuenios()} /
 * {@code listarVeterinarios()}) ni ningún campo administrativo. Mantener un
 * DTO separado evita que un campo añadido en el futuro a la vista de
 * administración se filtre automáticamente a un endpoint con audiencia más
 * amplia (ver {@code UsuarioController./duenios} y {@code /veterinarios}).
 */
public record UsuarioSeleccionableResponse(
        Long id,
        String nombre,
        String email,
        Rol rol
) {}
