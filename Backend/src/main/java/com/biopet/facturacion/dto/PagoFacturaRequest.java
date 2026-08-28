package com.biopet.facturacion.dto;

import com.biopet.facturacion.domain.FormaPagoSri;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Una forma de pago, tal y como el cliente la declara. Mapea 1:1 con
 * {@link com.biopet.facturacion.service.command.PagoBorradorCommand}. La suma
 * de todos los pagos debe cuadrar EXACTAMENTE con el total -eso lo comprueba
 * el servicio de emision, no este DTO-.
 *
 * @param formaPago    codigo de la TABLA 24. Obligatorio.
 * @param total        importe cubierto, positivo, hasta 2 decimales.
 * @param plazo        opcional, solo para pago a credito.
 * @param unidadTiempo opcional, unidad del plazo.
 */
public record PagoFacturaRequest(
        @NotNull FormaPagoSri formaPago,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal total,
        Integer plazo,
        String unidadTiempo
) {
}
