package com.biopet.facturacion.service.command;

import java.time.LocalDate;

/**
 * Cambios de cabecera admitidos mientras la factura sigue en BORRADOR.
 *
 * @param mascotaId    nueva mascota, o {@code null} para dejar la factura sin
 *                     contexto clinico. Ojo: quitar la mascota obliga a que
 *                     ninguna linea conserve origen clinico.
 * @param fechaEmision nueva fecha; al cambiarla cambia la tarifa aplicable, asi
 *                     que las lineas se recalculan.
 */
public record ActualizarFacturaBorradorCommand(Long mascotaId, LocalDate fechaEmision) {
}
