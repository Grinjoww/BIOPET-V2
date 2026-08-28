package com.biopet.facturacion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Cuerpo de {@code PUT /api/facturas/{id}/pagos}: sustituye TODAS las formas de
 * pago de una vez, igual que
 * {@link com.biopet.facturacion.service.FacturaBorradorService#reemplazarPagos}.
 * Una lista vacia es valida mientras el borrador siga incompleto.
 */
public record ReemplazarPagosRequest(@NotNull @Valid List<PagoFacturaRequest> pagos) {
}
