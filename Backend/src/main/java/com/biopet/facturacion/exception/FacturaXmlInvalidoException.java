package com.biopet.facturacion.exception;

/**
 * El XML de la factura no se puede producir o no cumple el esquema oficial.
 *
 * <p>Cubre tres situaciones que comparten la misma consecuencia -no hay
 * comprobante que enviar al SRI- y por eso no se desdoblan en tres excepciones:
 *
 * <ul>
 *   <li>falta un snapshot obligatorio para el XSD (la factura se emitio
 *       incompleta);</li>
 *   <li>los snapshots no reconcilian entre si (la suma de las lineas no cuadra
 *       con los totales de la cabecera);</li>
 *   <li>el XML generado no valida contra el XSD, o trae un DOCTYPE prohibido.</li>
 * </ul>
 *
 * <p>Fallar aqui es deliberado: es preferible no producir XML a producir uno
 * incoherente, que el SRI rechazaria con el error 52 ("error en los calculos del
 * comprobante") despues de haber consumido numeracion.
 */
public class FacturaXmlInvalidoException extends RuntimeException {

    public FacturaXmlInvalidoException(String mensaje) {
        super(mensaje);
    }

    public FacturaXmlInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
