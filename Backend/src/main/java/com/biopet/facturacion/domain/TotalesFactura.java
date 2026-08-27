package com.biopet.facturacion.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado completo del calculo de una factura.
 *
 * <p>Invariantes garantizados por {@link CalculoFacturaService}:
 * <pre>
 *   totalSinImpuestos = suma de precioTotalSinImpuesto de cada linea
 *   totalDescuento    = suma de descuentos de cada linea
 *   totalImpuestos    = suma de valorImpuesto de cada grupo
 *   importeTotal      = totalSinImpuestos + totalImpuestos
 * </pre>
 *
 * <p>No se modelan propina, fletes, seguros ni gastos aduaneros: son opcionales
 * en el XSD y BIOPET no los usa. Cuando se necesiten, {@code importeTotal}
 * debera incorporarlos.
 */
public record TotalesFactura(
        List<LineaCalculada> lineas,
        List<ImpuestoAgrupado> resumenImpuestos,
        BigDecimal totalSinImpuestos,
        BigDecimal totalDescuento,
        BigDecimal totalImpuestos,
        BigDecimal importeTotal
) {

    public TotalesFactura {
        lineas = List.copyOf(lineas);
        resumenImpuestos = List.copyOf(resumenImpuestos);
    }
}
