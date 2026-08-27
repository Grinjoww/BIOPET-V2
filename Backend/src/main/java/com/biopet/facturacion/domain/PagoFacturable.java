package com.biopet.facturacion.domain;

import java.math.BigDecimal;

/**
 * Una forma de pago de la factura ({@code <pagos>/<pago>}). Los campos
 * {@code plazo} y {@code unidadTiempo} del XSD son opcionales y no participan
 * en ninguna validacion aritmetica, por lo que no se modelan en esta fase.
 */
public record PagoFacturable(FormaPagoSri formaPago, BigDecimal total) {

    public PagoFacturable {
        if (formaPago == null) {
            throw new IllegalArgumentException("La forma de pago es obligatoria.");
        }
        EscalasSri.exigirNoNegativo(total, "El total del pago");
        EscalasSri.exigirEscalaMaxima(total, EscalasSri.ESCALA_MONETARIA, "El total del pago");
    }
}
