package com.biopet.facturacion.firma;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * Clave privada y certificado con los que se firma un comprobante.
 *
 * <p>Es un objeto de vida corta: lo produce {@link CertificadoFirmaProvider} en
 * el momento de firmar y no se cachea. No tiene {@code toString} propio a
 * proposito -el de un record imprimiria la clave privada- asi que se sobrescribe
 * para que un log accidental no vuelque material criptografico.
 */
public record MaterialFirma(PrivateKey privateKey, X509Certificate certificate) {

    public MaterialFirma {
        if (privateKey == null || certificate == null) {
            throw new IllegalArgumentException("El material de firma esta incompleto.");
        }
    }

    public InformacionCertificado informacion() {
        return InformacionCertificado.de(certificate);
    }

    @Override
    public String toString() {
        // Nunca la clave. Solo lo que ya viaja publicamente en el comprobante.
        return "MaterialFirma[subject=" + certificate.getSubjectX500Principal().getName()
                + ", serial=" + certificate.getSerialNumber() + "]";
    }
}
