package com.biopet.facturacion.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Datos de entrada para abrir un BORRADOR nuevo.
 *
 * <p>Deliberadamente pobre: solo referencias por id (que el backend resuelve y
 * valida) y una fecha. Nada de precio, impuesto, secuencial, clave de acceso,
 * codigo numerico, snapshot del emisor ni estado fiscal -eso lo calcula el
 * pipeline existente, nunca el cliente. Mapea 1:1 con
 * {@link com.biopet.facturacion.service.command.CrearFacturaBorradorCommand};
 * no se acepta la entidad {@code Factura} desde el exterior.
 *
 * @param usuarioId    propietario funcional de la factura (quien la vera en
 *                     "mis facturas"). Solo lo pueden crear ADMIN/AUXILIAR/
 *                     VETERINARIO, siempre en nombre de un cliente, asi que es
 *                     obligatorio: no hay un "self" implicito que asumir aqui,
 *                     a diferencia de DUENO en otros formularios del proyecto
 *                     (que ni siquiera llega a este endpoint).
 * @param mascotaId    opcional; si se informa debe pertenecer a usuarioId (lo
 *                     valida {@code FacturaBorradorService}).
 * @param fechaEmision obligatoria: gobierna la tarifa aplicable.
 */
public record CrearFacturaRequest(
        @NotNull Long usuarioId,
        Long mascotaId,
        @NotNull LocalDate fechaEmision
) {
}
