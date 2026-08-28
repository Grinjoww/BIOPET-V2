package com.biopet.facturacion.exception;

/**
 * La trazabilidad clinica de una linea no cuadra: el registro no existe, no
 * pertenece a la mascota de la factura, la factura no tiene mascota, o se trata
 * de una cita que todavia no se completo.
 *
 * <p>El origen es solo trazabilidad -no aporta precio ni impuesto- pero si es
 * incorrecto deja el comprobante mintiendo sobre que atencion respalda cada
 * linea, y eso es lo que se revisa cuando alguien reclama una factura.
 */
public class OrigenClinicoInvalidoException extends RuntimeException {

    public OrigenClinicoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
