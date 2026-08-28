package com.biopet.facturacion.dto;

import com.biopet.facturacion.entity.OrigenDetalleFactura;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Una linea tal y como la pide el cliente. Mapea 1:1 con
 * {@link com.biopet.facturacion.service.command.DetalleBorradorCommand}, y por
 * el mismo motivo que alli: sin precio, sin codigo de impuesto, sin tarifa, sin
 * descripcion. Todo eso lo pone el backend a partir de
 * {@code ConceptoFacturable}; aceptarlo aqui permitiria que un cliente se
 * facturase a si mismo cualquier precio o tarifa de IVA.
 *
 * @param conceptoFacturableId concepto activo del catalogo. Obligatorio.
 * @param cantidad             obligatoria, positiva, hasta 6 decimales (misma
 *                             escala que {@code EscalasSri}).
 * @param descuento            opcional ({@code null} = 0), no negativo, hasta 2
 *                             decimales.
 * @param origenTipo           trazabilidad clinica opcional.
 * @param origenId             id del registro clinico; va junto a origenTipo.
 */
public record DetalleFacturaRequest(
        @NotNull Long conceptoFacturableId,
        @NotNull @DecimalMin(value = "0.000001") @Digits(integer = 12, fraction = 6) BigDecimal cantidad,
        @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal descuento,
        OrigenDetalleFactura origenTipo,
        Long origenId
) {
}
