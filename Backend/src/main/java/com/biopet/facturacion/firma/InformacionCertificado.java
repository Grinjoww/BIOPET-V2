package com.biopet.facturacion.firma;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.Instant;

/**
 * Metadatos publicos del certificado de firma, aptos para diagnostico y para un
 * futuro endpoint de estado.
 *
 * <p>Contiene EXCLUSIVAMENTE datos que ya viajan dentro del propio comprobante
 * firmado (el certificado va en {@code ds:KeyInfo} en base64). Deliberadamente
 * NO expone la clave privada, la contrasena ni los bytes del PKCS#12: si algun
 * dia esto se serializa a JSON, no hay nada que se pueda filtrar por descuido.
 */
public record InformacionCertificado(
        String subject,
        String issuer,
        BigInteger serial,
        Instant notBefore,
        Instant notAfter
) {

    public static InformacionCertificado de(X509Certificate certificado) {
        return new InformacionCertificado(
                certificado.getSubjectX500Principal().getName(),
                certificado.getIssuerX500Principal().getName(),
                certificado.getSerialNumber(),
                certificado.getNotBefore().toInstant(),
                certificado.getNotAfter().toInstant());
    }
}
