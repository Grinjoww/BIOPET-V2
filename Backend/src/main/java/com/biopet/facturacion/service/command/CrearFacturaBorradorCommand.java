package com.biopet.facturacion.service.command;

import java.time.LocalDate;

/**
 * Datos minimos para abrir un borrador. Un borrador no necesita nacer completo:
 * ni comprador, ni lineas, ni pagos.
 *
 * @param usuarioId     propietario FUNCIONAL en BIOPET (quien vera la factura en
 *                      "mis facturas"). No tiene por que coincidir con el
 *                      comprador fiscal: el dueno de la mascota puede pedir la
 *                      factura a nombre de su empresa.
 * @param mascotaId     opcional. Si se informa, debe pertenecer al usuario.
 * @param fechaEmision  obligatoria: gobierna que tarifa se aplica y entra en la
 *                      clave de acceso.
 */
public record CrearFacturaBorradorCommand(Long usuarioId, Long mascotaId, LocalDate fechaEmision) {
}
