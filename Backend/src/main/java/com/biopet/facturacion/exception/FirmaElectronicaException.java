package com.biopet.facturacion.exception;

/**
 * No se pudo producir o verificar la firma XAdES del comprobante.
 *
 * <p>Cubre el documento de entrada inadecuado (raiz que no es {@code factura},
 * sin {@code id="comprobante"}, o ya firmado), el fallo de la propia operacion
 * criptografica, y el caso mas importante: que la firma recien producida NO
 * verifique. En ese ultimo supuesto no se persiste nada, porque un comprobante
 * con una firma que no valida es peor que un comprobante sin firmar.
 */
public class FirmaElectronicaException extends RuntimeException {

    public FirmaElectronicaException(String mensaje) {
        super(mensaje);
    }

    public FirmaElectronicaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
