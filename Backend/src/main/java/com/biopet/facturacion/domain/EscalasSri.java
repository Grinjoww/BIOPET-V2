package com.biopet.facturacion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Escalas y modo de redondeo del modulo de facturacion.
 *
 * <p><b>Lo que exige el SRI.</b> Ficha v2.34, seccion 9.17, literal: "El formato
 * para todo campo correspondiente a valores sera 123456.98 utilizando el punto
 * como separador de decimales; se utilizara como maximo dos decimales, a
 * excepcion de los campos de precio unitario y cantidad que se podra utilizar
 * hasta 6 decimales". El XSD oficial de factura lo refuerza con facets:
 * {@code cantidad} y {@code precioUnitario} son {@code totalDigits=18,
 * fractionDigits=6}; {@code descuento}, {@code precioTotalSinImpuesto},
 * {@code baseImponible}, {@code valor}, {@code importeTotal},
 * {@code totalSinImpuestos} y {@code total} (pago) son
 * {@code totalDigits=14, fractionDigits=2}; {@code tarifa} es
 * {@code totalDigits=4, fractionDigits=2}.
 *
 * <p><b>Lo que NO exige el SRI y decidimos nosotros.</b> Ni la ficha ni el XSD
 * indican el modo de redondeo (HALF_UP, HALF_EVEN, ...) ni el orden entre
 * redondear y sumar. Se elige {@link RoundingMode#HALF_UP} por ser el redondeo
 * comercial habitual y el que coincide con el unico ejemplo numerico real
 * publicado en la ficha. Ver {@link CalculoFacturaService} para la politica
 * completa.
 */
public final class EscalasSri {

    /** Decimales de cantidad y precio unitario (XSD: fractionDigits=6). */
    public static final int ESCALA_CANTIDAD = 6;

    /** Decimales de todo importe monetario (XSD: fractionDigits=2). */
    public static final int ESCALA_MONETARIA = 2;

    /** Decimales de la tarifa porcentual (XSD: fractionDigits=2). */
    public static final int ESCALA_TARIFA = 2;

    /** Decision de implementacion, no norma del SRI. */
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    public static final BigDecimal CIEN = new BigDecimal("100");

    private EscalasSri() {
    }

    /** Redondea a los 2 decimales con los que todo importe viaja en el XML. */
    public static BigDecimal aMonetario(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, REDONDEO);
    }

    static void exigirNoNulo(BigDecimal valor, String etiqueta) {
        if (valor == null) {
            throw new IllegalArgumentException(etiqueta + " es obligatorio.");
        }
    }

    static void exigirNoNegativo(BigDecimal valor, String etiqueta) {
        exigirNoNulo(valor, etiqueta);
        if (valor.signum() < 0) {
            throw new IllegalArgumentException(etiqueta + " no puede ser negativo; se recibio " + valor + ".");
        }
    }

    static void exigirEscalaMaxima(BigDecimal valor, int escalaMaxima, String etiqueta) {
        exigirNoNulo(valor, etiqueta);
        if (valor.stripTrailingZeros().scale() > escalaMaxima) {
            throw new IllegalArgumentException(
                    etiqueta + " admite como maximo " + escalaMaxima + " decimales; se recibio " + valor + ".");
        }
    }
}
