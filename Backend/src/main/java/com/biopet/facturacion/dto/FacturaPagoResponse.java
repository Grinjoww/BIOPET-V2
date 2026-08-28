package com.biopet.facturacion.dto;

import com.biopet.facturacion.domain.FormaPagoSri;

import java.math.BigDecimal;

/** Una forma de pago del comprobante. Solo lectura. */
public record FacturaPagoResponse(
        FormaPagoSri formaPago,
        BigDecimal total,
        Integer plazo,
        String unidadTiempo
) {
}
