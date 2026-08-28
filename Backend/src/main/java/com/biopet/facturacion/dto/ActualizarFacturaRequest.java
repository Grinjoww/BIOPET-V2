package com.biopet.facturacion.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Cambios de cabecera admitidos mientras la factura sigue en BORRADOR. Mapea
 * 1:1 con {@link com.biopet.facturacion.service.command.ActualizarFacturaBorradorCommand}.
 *
 * @param mascotaId    nueva mascota, o {@code null} para quitarla.
 * @param fechaEmision nueva fecha; recalcula las lineas con la tarifa vigente.
 */
public record ActualizarFacturaRequest(
        Long mascotaId,
        @NotNull LocalDate fechaEmision
) {
}
