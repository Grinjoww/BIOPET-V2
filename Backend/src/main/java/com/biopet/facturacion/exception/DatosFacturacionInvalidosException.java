package com.biopet.facturacion.exception;

/**
 * La identidad tributaria del comprador no sirve para emitir: falta el tipo de
 * identificacion, la identificacion o la razon social, o los datos elegidos no
 * pertenecen al titular de la factura.
 *
 * <p>Se comprueba coherencia ESTRUCTURAL, no validez normativa: verificar que
 * un RUC o una cedula existan de verdad es competencia del SRI, que lo resuelve
 * al autorizar.
 */
public class DatosFacturacionInvalidosException extends RuntimeException {

    public DatosFacturacionInvalidosException(String mensaje) {
        super(mensaje);
    }
}
