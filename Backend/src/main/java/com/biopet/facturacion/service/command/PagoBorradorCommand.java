package com.biopet.facturacion.service.command;

import com.biopet.facturacion.domain.FormaPagoSri;

import java.math.BigDecimal;

/**
 * Una forma de pago del comprobante.
 *
 * <p>La suma de todos los pagos debe ser EXACTAMENTE el importe total; la
 * comprobacion la hace {@code CalculoFacturaService.validarPagos} con
 * BigDecimal, sin tolerancias.
 *
 * @param formaPago    codigo de la TABLA 24. Obligatorio.
 * @param total        importe cubierto por esta forma de pago, hasta 2 decimales.
 * @param plazo        opcional, solo para pago a credito.
 * @param unidadTiempo opcional, unidad del plazo (por ejemplo "dias").
 */
public record PagoBorradorCommand(
        FormaPagoSri formaPago,
        BigDecimal total,
        Integer plazo,
        String unidadTiempo
) {

    public static PagoBorradorCommand de(FormaPagoSri formaPago, BigDecimal total) {
        return new PagoBorradorCommand(formaPago, total, null, null);
    }
}
