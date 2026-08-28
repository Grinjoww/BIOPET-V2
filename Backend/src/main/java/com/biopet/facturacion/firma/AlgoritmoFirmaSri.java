package com.biopet.facturacion.firma;

/**
 * Pareja (algoritmo de firma, algoritmo de digest) admitida por el modulo.
 *
 * <p><b>El valor por defecto es {@link #RSA_SHA1}</b> porque es lo que muestra
 * el ANEXO 14 de la Ficha Tecnica de Comprobantes Electronicos Offline del SRI
 * y lo que su esquema de firma describe. No es una eleccion de seguridad: SHA-1
 * lleva años desaconsejado para usos criptograficos generales, pero aqui manda
 * el perfil del receptor, no nuestra preferencia.
 *
 * <p>{@link #RSA_SHA256} queda cableado y probado porque el spike de la Fase 3
 * demostro que xades4j lo produce sin esfuerzo adicional en Java 21. Que BIOPET
 * pueda generarlo <b>no significa que el SRI lo acepte</b>: eso solo puede
 * afirmarse tras enviar un comprobante a CELCER, cosa que todavia no se ha
 * hecho. Por eso no es el valor por defecto.
 *
 * <p><b>Ninguno de los dos exige relajar la seguridad al verificar.</b> Apache
 * Santuario 4.0.4 acepta RSA-SHA1 con su validacion segura ACTIVADA; quien lo
 * bloquea es el proveedor XMLDSig del JDK ({@code javax.xml.crypto.dsig}), que
 * no se usa aqui. Ver {@code FirmaXadesVerificador}.
 */
public enum AlgoritmoFirmaSri {

    RSA_SHA1(
            "http://www.w3.org/2000/09/xmldsig#rsa-sha1",
            "http://www.w3.org/2000/09/xmldsig#sha1"),

    RSA_SHA256(
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
            "http://www.w3.org/2001/04/xmlenc#sha256");

    private final String uriFirma;
    private final String uriDigest;

    AlgoritmoFirmaSri(String uriFirma, String uriDigest) {
        this.uriFirma = uriFirma;
        this.uriDigest = uriDigest;
    }

    /** URI de {@code ds:SignatureMethod}. */
    public String uriFirma() {
        return uriFirma;
    }

    /** URI de {@code ds:DigestMethod} de las tres referencias. */
    public String uriDigest() {
        return uriDigest;
    }

}
