package com.biopet.facturacion.dto;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.entity.OrigenDetalleFactura;

import java.math.BigDecimal;

/** Una linea del comprobante, ya calculada. Solo lectura. */
public record FacturaDetalleResponse(
        Integer linea,
        Long conceptoFacturableId,
        String codigoPrincipal,
        String descripcion,
        BigDecimal cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuento,
        BigDecimal precioTotalSinImpuesto,
        CodigoImpuestoSri impuestoCodigo,
        String impuestoCodigoPorcentaje,
        BigDecimal impuestoTarifa,
        BigDecimal baseImponible,
        BigDecimal impuestoValor,
        OrigenDetalleFactura origenTipo,
        Long origenId
) {
}
