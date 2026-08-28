package com.biopet.facturacion.firma;

import com.biopet.facturacion.exception.FirmaElectronicaException;
import com.biopet.facturacion.xml.FacturaXmlBuilder;
import org.springframework.stereotype.Component;
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

/**
 * Produce la firma XAdES-BES ENVELOPED sobre el XML de un comprobante.
 *
 * <h2>Perfil</h2>
 *
 * <p>La configuracion reproduce la estructura del ANEXO 14 de la Ficha Tecnica
 * de Comprobantes Electronicos Offline del SRI ("EJEMPLO FIRMA ELECTRONICA BAJO
 * ESTANDAR XADES_BES"): XAdES 1.3.2, ENVELOPED, canonicalizacion C14N 1.0 sin
 * comentarios y <b>tres</b> {@code ds:Reference} -al comprobante, a
 * {@code SignedProperties} y a {@code ds:KeyInfo}-.
 *
 * <p>La tercera referencia solo aparece si se activa {@code signKeyInfo}; xades4j
 * NO la genera por defecto. Se activa a proposito, porque la seccion 6.5 de la
 * ficha exige que {@code ds:KeyInfo} lleve el certificado del firmante y que esa
 * informacion tambien este firmada. Es lo que impide sustituir el certificado
 * publicado sin romper la firma.
 *
 * <h2>Que NO hace</h2>
 *
 * <p>No reconstruye la factura desde la base de datos ni consulta ninguna
 * relacion: recibe los bytes EXACTOS de {@code XML_GENERADO} y firma eso. Si
 * regenerase el documento antes de firmar, podria firmar algo distinto de lo que
 * quedo persistido, y el comprobante enviado al SRI dejaria de corresponder al
 * que BIOPET guarda.
 */
@Component
public class FacturaXadesSigner {

    public static final String NS_DSIG = "http://www.w3.org/2000/09/xmldsig#";
    public static final String NS_XADES_1_3_2 = "http://uri.etsi.org/01903/v1.3.2#";
    public static final String TYPE_SIGNED_PROPERTIES = "http://uri.etsi.org/01903#SignedProperties";
    public static final String C14N_1_0 = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315";

    /**
     * @param xmlGenerado bytes exactos del documento a firmar.
     * @param material    clave privada y certificado.
     * @param algoritmo   perfil de algoritmos; RSA-SHA1 para el SRI.
     * @return el comprobante firmado, serializado en UTF-8.
     */
    public byte[] firmar(byte[] xmlGenerado, MaterialFirma material, AlgoritmoFirmaSri algoritmo) {
        if (xmlGenerado == null || xmlGenerado.length == 0) {
            throw new FirmaElectronicaException("No hay XML que firmar.");
        }
        if (material == null) {
            throw new FirmaElectronicaException("No hay material de firma.");
        }
        if (algoritmo == null) {
            throw new FirmaElectronicaException("No se indico el algoritmo de firma.");
        }

        Document documento = XmlFirmaSeguro.parsear(xmlGenerado);
        Element comprobante = exigirComprobanteFirmable(documento);

        try {
            firmarDocumento(comprobante, material, algoritmo);
        } catch (FirmaElectronicaException e) {
            throw e;
        } catch (Exception e) {
            throw new FirmaElectronicaException(
                    "No se pudo firmar el comprobante: " + e.getMessage(), e);
        }

        return XmlFirmaSeguro.serializar(documento);
    }

    /**
     * Comprueba que el documento es una factura sin firmar y apta para resolver
     * la URI {@code #comprobante}.
     */
    private Element exigirComprobanteFirmable(Document documento) {
        Element raiz = documento.getDocumentElement();
        if (raiz == null || !"factura".equals(raiz.getNodeName())) {
            throw new FirmaElectronicaException(
                    "El documento a firmar no es una factura; su raiz es "
                            + (raiz == null ? "inexistente" : "<" + raiz.getNodeName() + ">") + ".");
        }
        if (!FacturaXmlBuilder.ID_COMPROBANTE.equals(raiz.getAttribute("id"))) {
            throw new FirmaElectronicaException(
                    "La factura debe llevar id=\"" + FacturaXmlBuilder.ID_COMPROBANTE
                            + "\"; sin ese identificador la referencia #"
                            + FacturaXmlBuilder.ID_COMPROBANTE + " no puede resolverse.");
        }
        if (documento.getElementsByTagNameNS(NS_DSIG, "Signature").getLength() > 0) {
            // Volver a firmar produciria un documento con dos firmas, y la
            // segunda cubriria a la primera. Nunca es lo que se quiere.
            throw new FirmaElectronicaException(
                    "El comprobante ya contiene una firma: no se vuelve a firmar.");
        }
        return raiz;
    }

    private void firmarDocumento(Element comprobante, MaterialFirma material,
                                 AlgoritmoFirmaSri algoritmo) throws Exception {
        // Sin esto la URI "#comprobante" no resuelve durante la firma: el DOM no
        // sabe que ese atributo es un ID porque no se carga ningun DTD.
        comprobante.setIdAttribute("id", true);

        KeyingDataProvider proveedorClaves =
                new DirectKeyingDataProvider(material.certificate(), material.privateKey());

        SignatureAlgorithms algoritmos = new SignatureAlgorithms()
                .withSignatureAlgorithm("RSA", algoritmo.uriFirma())
                .withDigestAlgorithmForDataObjectReferences(algoritmo.uriDigest())
                .withDigestAlgorithmForReferenceProperties(algoritmo.uriDigest())
                .withCanonicalizationAlgorithmForSignature(new CanonicalXMLWithoutComments());

        BasicSignatureOptions opciones = new BasicSignatureOptions()
                // Certificado X.509 en base64 dentro de ds:KeyInfo (seccion 6.5).
                .includeSigningCertificate(SigningCertificateMode.SIGNING_CERTIFICATE)
                // ds:KeyValue/ds:RSAKeyValue, presente en el ANEXO 14.
                .includePublicKey(true)
                // Firma ds:KeyInfo: genera la tercera ds:Reference.
                .signKeyInfo(true)
                // etsi:IssuerSerial dentro de SigningCertificate.
                .includeIssuerSerial(true)
                // Se dejan ACTIVAS: no se relaja nada para que la firma salga.
                // El proveedor ya rechazo antes un certificado que no sirviese,
                // asi que esto es una segunda barrera, no la unica.
                .checkKeyUsage(true)
                .checkCertificateValidity(true);

        XadesSigner firmante = new XadesBesSigningProfile(proveedorClaves)
                .withSignatureAlgorithms(algoritmos)
                .withBasicSignatureOptions(opciones)
                .newSigner();

        DataObjectDesc referencia = new DataObjectReference("#" + FacturaXmlBuilder.ID_COMPROBANTE)
                .withTransform(new EnvelopedSignatureTransform());

        firmante.sign(new SignedDataObjects(referencia), comprobante);
    }
}
