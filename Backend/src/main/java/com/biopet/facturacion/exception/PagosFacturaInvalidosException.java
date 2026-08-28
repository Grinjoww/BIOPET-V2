package com.biopet.facturacion.exception;

/**
 * Las formas de pago no cubren exactamente el importe total.
 *
 * <p>La comparacion es exacta con BigDecimal en escala fiscal: no hay
 * tolerancias del tipo {@code Math.abs(a - b) < 0.01}. Un centavo de diferencia
 * es el error 52 del SRI ("error en los calculos del comprobante"), asi que se
 * detecta aqui y no en la autorizacion.
 */
public class PagosFacturaInvalidosException extends RuntimeException {

    public PagosFacturaInvalidosException(String mensaje) {
        super(mensaje);
    }
}
