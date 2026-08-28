package com.biopet.facturacion.exception;

/**
 * El material de firma no sirve: falta configuracion, el PKCS#12 no se puede
 * abrir, la contrasena no es correcta, o el certificado no cumple los requisitos
 * de una firma electronica (no es RSA, no llega a 2048 bits, esta caducado,
 * todavia no es valido, la clave no corresponde al certificado, o su KeyUsage no
 * permite firmar).
 *
 * <p>El mensaje describe el problema pero <b>nunca</b> incluye la contrasena, la
 * clave privada ni los bytes del almacen.
 */
public class CertificadoFirmaInvalidoException extends RuntimeException {

    public CertificadoFirmaInvalidoException(String mensaje) {
        super(mensaje);
    }

    public CertificadoFirmaInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
