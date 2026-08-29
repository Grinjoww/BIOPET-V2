package com.biopet.facturacion.dto;

import jakarta.validation.constraints.Size;

/**
 * Edicion de un punto de emision ya existente (ADMIN).
 *
 * <p>Deliberadamente pobre: solo la direccion del establecimiento es editable
 * por REST. {@code emisorFiscalId}, {@code establecimiento} y
 * {@code puntoEmision} son la identidad de la serie fiscal y no se aceptan
 * aqui -ver el javadoc de {@link PuntoEmisionRequest}-; activar/desactivar es
 * {@code PATCH /estado}, un cambio de estado distinto de una edicion.
 */
public record ActualizarPuntoEmisionRequest(@Size(max = 300) String direccionEstablecimiento) {
}
