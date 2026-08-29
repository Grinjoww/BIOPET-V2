package com.biopet.facturacion.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo comun de los {@code PATCH .../estado} de la Fase 8B
 * (conceptos facturables, puntos de emision, tarifas de impuesto): activa o
 * desactiva LOGICAMENTE el recurso, nunca lo borra. Un solo DTO reutilizado
 * porque en los tres casos la operacion es identica -cambiar un booleano- y no
 * hay ningun otro campo que mezclar con ella.
 */
public record EstadoRequest(@NotNull Boolean activo) {
}
