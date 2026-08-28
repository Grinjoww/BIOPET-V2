package com.biopet.facturacion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Cuerpo de {@code PUT /api/facturas/{id}/detalles}: sustituye TODAS las
 * lineas del borrador de una vez, igual que
 * {@link com.biopet.facturacion.service.FacturaBorradorService#reemplazarDetalles}.
 *
 * <p>No hay alta/baja/edicion linea a linea porque el servicio de dominio no la
 * ofrece (la numeracion debe quedar contigua y cualquier cambio recalcula la
 * factura entera): este DTO no inventa una operacion que el backend no soporta,
 * solo envuelve la lista para que Bean Validation valide cada elemento
 * ({@code @Valid} en el campo, no solo en el parametro del controlador).
 *
 * <p>Una lista vacia es valida: un borrador a medio construir puede no tener
 * lineas todavia.
 */
public record ReemplazarDetallesRequest(@NotNull @Valid List<DetalleFacturaRequest> detalles) {
}
