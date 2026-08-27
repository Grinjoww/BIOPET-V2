package com.biopet.facturacion.domain;

import java.math.BigDecimal;

/**
 * Un grupo de {@code <totalConImpuestos>/<totalImpuesto>}: la suma de todas las
 * lineas que comparten el mismo par (codigo de impuesto, codigo de porcentaje).
 *
 * <p>Una factura puede tener varios grupos a la vez, por ejemplo un servicio
 * gravado y un producto con tarifa 0%. No se asume una unica tarifa por factura.
 */
public record ImpuestoAgrupado(
        CodigoImpuestoSri codigoImpuesto,
        String codigoPorcentaje,
        BigDecimal tarifa,
        BigDecimal baseImponible,
        BigDecimal valorImpuesto
) {
}
