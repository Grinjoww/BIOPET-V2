package com.biopet.facturacion.spike;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import xades4j.algorithms.CanonicalXMLWithoutComments;
import xades4j.algorithms.EnvelopedSignatureTransform;
import xades4j.production.BasicSignatureOptions;
import xades4j.production.DataObjectReference;
import xades4j.production.SignatureAlgorithms;
import xades4j.production.SignedDataObjects;
import xades4j.production.SigningCertificateMode;
import xades4j.production.XadesBesSigningProfile;
import xades4j.production.XadesSigner;
import xades4j.properties.DataObjectDesc;
import xades4j.providers.KeyingDataProvider;
import xades4j.providers.impl.DirectKeyingDataProvider;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * SPIKE FASE 3 - Envoltura minima de xades4j para producir XAdES-BES ENVELOPED.
 *
 * <p>La configuracion reproduce deliberadamente la estructura del ANEXO 14 de la
 * Ficha Tecnica del SRI v2.34 ("EJEMPLO FIRMA ELECTRONICA BAJO ESTANDAR
 * XADES_BES"), que muestra <b>tres</b> {@code ds:Reference}:
 *
 * <ol>
 *   <li>a {@code SignedProperties}, con
 *       {@code Type="http://uri.etsi.org/01903#SignedProperties"};</li>
 *   <li>a {@code KeyInfo} ({@code URI="#Certificate..."});</li>
 *   <li>al comprobante ({@code URI="#comprobante"}) con transform
 *       enveloped-signature.</li>
 * </ol>
 *
 * <p>La referencia (2) solo aparece si se activa
 * {@link BasicSignatureOptions#signKeyInfo(boolean)}; por defecto xades4j NO la
 * genera. Se activa aqui explicitamente para alinearse con el anexo del SRI y
 * con su seccion 6.5 ("Es necesario utilizar el elemento ds:KeyInfo,
 * conteniendo al menos el certificado firmante codificado en base64. Ademas,
 * dicha informacion precisa ser firmada").
 *
 * <p>La canonicalizacion se fija a C14N 1.0 sin comentarios
 * ({@code REC-xml-c14n-20010315}), que es la que aparece en el anexo.
 */
final class XadesSpikeSigner {

    /** URIs de algoritmo, para que los tests afirmen sobre valores exactos. */
    static final String SIG_RSA_SHA1 = "http://www.w3.org/2000/09/xmldsig#rsa-sha1";
    static final String SIG_RSA_SHA256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    static final String DIGEST_SHA1 = "http://www.w3.org/2000/09/xmldsig#sha1";
    static final String DIGEST_SHA256 = "http://www.w3.org/2001/04/xmlenc#sha256";
    static final String C14N_1_0 = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315";

    static final String NS_DSIG = "http://www.w3.org/2000/09/xmldsig#";
    static final String NS_XADES_1_3_2 = "http://uri.etsi.org/01903/v1.3.2#";
    static final String TYPE_SIGNED_PROPERTIES = "http://uri.etsi.org/01903#SignedProperties";

    private XadesSpikeSigner() {
    }

    /**
     * Firma en ENVELOPED el elemento cuyo atributo {@code id} vale
     * {@code idComprobante}, insertando la firma como ultimo hijo de ese mismo
     * elemento.
     *
     * @param algoritmoFirma  URI de {@code ds:SignatureMethod}.
     * @param algoritmoDigest URI de {@code ds:DigestMethod} de las referencias.
     */
    static void firmar(Document documento,
                       String idComprobante,
                       PrivateKey privateKey,
                       X509Certificate certificate,
                       String algoritmoFirma,
                       String algoritmoDigest) throws Exception {

        Element comprobante = documento.getDocumentElement();
        // Sin esto la URI "#comprobante" no resuelve durante la firma.
        comprobante.setIdAttribute("id", true);

        KeyingDataProvider proveedorClaves = new DirectKeyingDataProvider(certificate, privateKey);

        SignatureAlgorithms algoritmos = new SignatureAlgorithms()
                .withSignatureAlgorithm("RSA", algoritmoFirma)
                .withDigestAlgorithmForDataObjectReferences(algoritmoDigest)
                .withDigestAlgorithmForReferenceProperties(algoritmoDigest)
                .withCanonicalizationAlgorithmForSignature(new CanonicalXMLWithoutComments());

        BasicSignatureOptions opciones = new BasicSignatureOptions()
                // El certificado X509 en base64 dentro de ds:KeyInfo (seccion 6.5).
                .includeSigningCertificate(SigningCertificateMode.SIGNING_CERTIFICATE)
                // ds:KeyValue/ds:RSAKeyValue, presente en el ANEXO 14.
                .includePublicKey(true)
                // Firma ds:KeyInfo -> genera la 2a ds:Reference del anexo.
                .signKeyInfo(true)
                // etsi:IssuerSerial dentro de SigningCertificate, presente en el anexo.
                .includeIssuerSerial(true)
                // El certificado de prueba lleva digitalSignature|nonRepudiation:
                // se dejan ACTIVAS ambas comprobaciones, no se relaja nada.
                .checkKeyUsage(true)
                .checkCertificateValidity(true);

        XadesSigner firmante = new XadesBesSigningProfile(proveedorClaves)
                .withSignatureAlgorithms(algoritmos)
                .withBasicSignatureOptions(opciones)
                .newSigner();

        // withTransform() devuelve DataObjectDesc (tipo base), no DataObjectReference.
        DataObjectDesc referencia = new DataObjectReference("#" + idComprobante)
                .withTransform(new EnvelopedSignatureTransform());

        firmante.sign(new SignedDataObjects(referencia), comprobante);
    }
}
