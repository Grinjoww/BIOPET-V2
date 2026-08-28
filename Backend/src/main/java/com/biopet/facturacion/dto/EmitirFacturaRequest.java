package com.biopet.facturacion.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Orden de emision.
 *
 * <p>Deliberadamente SIN campo {@code ambiente}. PRUEBAS/PRODUCCION no es una
 * decision que el cliente REST pueda tomar: es una propiedad del despliegue,
 * resuelta en el backend por {@code SriAmbienteProperties} (propiedad
 * {@code sri.ambiente}) y nunca leida del request. Ver el javadoc de esa clase
 * para el porque -esta era, antes de esta correccion, la unica via por la que
 * un cliente HTTP podia intentar pedir PRODUCCION-.
 *
 * @param puntoEmisionId punto de emision activo desde el que se numera.
 */
public record EmitirFacturaRequest(
        @NotNull Long puntoEmisionId
) {
}
