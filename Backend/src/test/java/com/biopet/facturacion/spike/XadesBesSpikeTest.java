package com.biopet.facturacion.spike;

import org.apache.xml.security.signature.XMLSignature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPIKE FASE 3 - Viabilidad de XAdES-BES en Java 21 con xades4j 2.4.1.
 *
 * <p>Experimento A: RSA-SHA1 (lo que la Ficha v2.34 seccion 6.8 indica
 * literalmente). Experimento B: RSA-SHA256 (alternativa moderna).
 *
 * <p>El XML usado es un fragmento de prueba con forma de factura, NO una factura
 * SRI valida. El objetivo es la firma, no el comprobante.
 *
 * <p>Ninguna propiedad global de seguridad se modifica. La validacion segura se
 * controla por instancia mediante el constructor publico
 * {@code XMLSignature(Element, String, boolean)} de Apache Santuario.
 */
class XadesBesSpikeTest {

    private static final String ID_COMPROBANTE = "comprobante";

    /** Fragmento ficticio: estructura realista, contenido inventado. */
    private static final String XML_PRUEBA = """
            <factura id="comprobante" version="2.1.0">
              <infoTributaria>
                <ambiente>1</ambiente>
                <tipoEmision>1</tipoEmision>
                <razonSocial>EMISOR DE PRUEBA SPIKE</razonSocial>
                <ruc>9999999999999</ruc>
                <codDoc>01</codDoc>
                <estab>001</estab>
                <ptoEmi>001</ptoEmi>
                <secuencial>000000001</secuencial>
              </infoTributaria>
              <detalles>
                <detalle>
                  <descripcion>CONCEPTO DE PRUEBA</descripcion>
                  <cantidad>1.000000</cantidad>
                  <precioUnitario>10.000000</precioUnitario>
                  <precioTotalSinImpuesto>10.00</precioTotalSinImpuesto>
                </detalle>
              </detalles>
            </factura>
            """;

    private static Path p12;
    private static CertificadoPruebaP12.Material material;

    @BeforeAll
    static void generarCertificadoDePrueba() throws Exception {
        org.apache.xml.security.Init.init();
        p12 = Path.of("target", "tmp", "spike-xades-" + System.nanoTime() + ".p12");
        material = CertificadoPruebaP12.generar(p12, 2);
    }

    @AfterAll
    static void borrarCertificadoDePrueba() throws Exception {
        if (p12 != null) {
            CertificadoPruebaP12.limpiar(p12);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String firmar(String algoritmoFirma, String algoritmoDigest) throws Exception {
        Document documento = XmlSeguro.parsear(XML_PRUEBA);
        XadesSpikeSigner.firmar(documento, ID_COMPROBANTE,
                material.privateKey(), material.certificate(),
                algoritmoFirma, algoritmoDigest);
        return XmlSeguro.serializar(documento);
    }

    /**
     * Verificacion criptografica completa sobre el XML YA SERIALIZADO y vuelto a
     * parsear, que es exactamente el artefacto que se enviaria al SRI.
     *
     * @param validacionSegura valor pasado al constructor de Santuario. No se
     *                         toca ninguna propiedad global del proceso.
     */
    private boolean verificar(String xmlFirmado, boolean validacionSegura) throws Exception {
        Document documento = XmlSeguro.parsear(xmlFirmado);
        XmlSeguro.registrarAtributosId(documento);

        Element firma = (Element) documento
                .getElementsByTagNameNS(XadesSpikeSigner.NS_DSIG, "Signature").item(0);
        assertNotNull(firma, "No se encontro ds:Signature");

        XMLSignature xmlSignature = new XMLSignature(firma, "", validacionSegura);
        X509Certificate certificado = certificadoDeKeyInfo(documento);
        return xmlSignature.checkSignatureValue(certificado);
    }

    /** Extrae el X509 del propio ds:KeyInfo, como haria un verificador externo. */
    private X509Certificate certificadoDeKeyInfo(Document documento) throws Exception {
        Element x509 = (Element) documento
                .getElementsByTagNameNS(XadesSpikeSigner.NS_DSIG, "X509Certificate").item(0);
        assertNotNull(x509, "No se encontro ds:X509Certificate en KeyInfo");
        byte[] der = Base64.getMimeDecoder().decode(x509.getTextContent().trim());
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(der));
    }

    private List<Element> referencias(Document documento) {
        NodeList nodos = documento.getElementsByTagNameNS(XadesSpikeSigner.NS_DSIG, "Reference");
        List<Element> lista = new ArrayList<>();
        for (int i = 0; i < nodos.getLength(); i++) {
            lista.add((Element) nodos.item(i));
        }
        return lista;
    }

    private Element unico(Document documento, String ns, String nombre) {
        NodeList nodos = documento.getElementsByTagNameNS(ns, nombre);
        assertEquals(1, nodos.getLength(), "Se esperaba exactamente un <" + nombre + ">");
        return (Element) nodos.item(0);
    }

    // ------------------------------------------------------------------
    // EXPERIMENTO A - RSA-SHA1 (lo que dice la ficha del SRI)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A1 - RSA-SHA1: xades4j PRODUCE la firma en Java 21")
    void experimentoA_generaFirmaRsaSha1() throws Exception {
        String firmado = firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1);

        Document documento = XmlSeguro.parsear(firmado);
        assertEquals(XadesSpikeSigner.SIG_RSA_SHA1,
                unico(documento, XadesSpikeSigner.NS_DSIG, "SignatureMethod").getAttribute("Algorithm"));
        for (Element referencia : referencias(documento)) {
            Element digest = (Element) referencia
                    .getElementsByTagNameNS(XadesSpikeSigner.NS_DSIG, "DigestMethod").item(0);
            assertEquals(XadesSpikeSigner.DIGEST_SHA1, digest.getAttribute("Algorithm"));
        }
    }

    @Test
    @DisplayName("A2 - RSA-SHA1: la firma VERIFICA criptograficamente")
    void experimentoA_verificaFirmaRsaSha1() throws Exception {
        String firmado = firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1);
        assertTrue(verificar(firmado, false), "La firma RSA-SHA1 deberia validar");
    }

    @Test
    @DisplayName("A3 - RSA-SHA1: alterar el XML firmado INVALIDA la firma")
    void experimentoA_alterarInvalidaLaFirma() throws Exception {
        String firmado = firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1);
        assertTrue(verificar(firmado, false), "precondicion: la firma original valida");

        String alterado = firmado.replace(
                "<precioTotalSinImpuesto>10.00</precioTotalSinImpuesto>",
                "<precioTotalSinImpuesto>99.99</precioTotalSinImpuesto>");
        assertFalse(firmado.equals(alterado), "el XML debe haber cambiado realmente");

        assertFalse(verificar(alterado, false),
                "Alterar un importe firmado DEBE invalidar la firma");
    }

    @Test
    @DisplayName("A4 - RSA-SHA1 bajo validacion segura de Santuario: comportamiento real")
    void experimentoA_validacionSegura() throws Exception {
        String firmado = firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1);
        boolean resultado;
        String excepcion = null;
        try {
            resultado = verificar(firmado, true);
        } catch (Exception ex) {
            resultado = false;
            excepcion = ex.getClass().getName() + ": " + ex.getMessage();
        }
        // No se afirma un valor concreto: este test DOCUMENTA el comportamiento
        // observado, que es justo el objeto del spike. El informe recoge el dato.
        System.out.println("[SPIKE A4] RSA-SHA1 con secureValidation=true -> resultado=" + resultado
                + (excepcion != null ? " | excepcion=" + excepcion : " | sin excepcion"));
    }

    // ------------------------------------------------------------------
    // EXPERIMENTO B - RSA-SHA256
    // ------------------------------------------------------------------

    @Test
    @DisplayName("B1 - RSA-SHA256: xades4j PRODUCE la firma en Java 21")
    void experimentoB_generaFirmaRsaSha256() throws Exception {
        String firmado = firmar(XadesSpikeSigner.SIG_RSA_SHA256, XadesSpikeSigner.DIGEST_SHA256);

        Document documento = XmlSeguro.parsear(firmado);
        assertEquals(XadesSpikeSigner.SIG_RSA_SHA256,
                unico(documento, XadesSpikeSigner.NS_DSIG, "SignatureMethod").getAttribute("Algorithm"));
        for (Element referencia : referencias(documento)) {
            Element digest = (Element) referencia
                    .getElementsByTagNameNS(XadesSpikeSigner.NS_DSIG, "DigestMethod").item(0);
            assertEquals(XadesSpikeSigner.DIGEST_SHA256, digest.getAttribute("Algorithm"));
        }
    }

    @Test
    @DisplayName("B2 - RSA-SHA256: la firma VERIFICA criptograficamente")
    void experimentoB_verificaFirmaRsaSha256() throws Exception {
        String firmado = firmar(XadesSpikeSigner.SIG_RSA_SHA256, XadesSpikeSigner.DIGEST_SHA256);
        assertTrue(verificar(firmado, false), "La firma RSA-SHA256 deberia validar");
    }

    @Test
    @DisplayName("B3 - RSA-SHA256: verifica tambien con validacion segura ACTIVADA")
    void experimentoB_verificaConValidacionSegura() throws Exception {
        String firmado = firmar(XadesSpikeSigner.SIG_RSA_SHA256, XadesSpikeSigner.DIGEST_SHA256);
        assertTrue(verificar(firmado, true),
                "RSA-SHA256 deberia validar incluso con secureValidation=true");
    }

    @Test
    @DisplayName("B4 - RSA-SHA256: alterar el XML firmado INVALIDA la firma")
    void experimentoB_alterarInvalidaLaFirma() throws Exception {
        String firmado = firmar(XadesSpikeSigner.SIG_RSA_SHA256, XadesSpikeSigner.DIGEST_SHA256);
        assertTrue(verificar(firmado, false), "precondicion: la firma original valida");

        String alterado = firmado.replace("EMISOR DE PRUEBA SPIKE", "EMISOR SUPLANTADO");
        assertFalse(firmado.equals(alterado), "el XML debe haber cambiado realmente");

        assertFalse(verificar(alterado, false),
                "Alterar la razon social firmada DEBE invalidar la firma");
    }

    // ------------------------------------------------------------------
    // ESTRUCTURA XAdES-BES frente al ANEXO 14 de la Ficha v2.34
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C1 - Las TRES ds:Reference del ANEXO 14 estan presentes")
    void estructura_tresReferencias() throws Exception {
        Document documento = XmlSeguro.parsear(
                firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1));

        List<Element> refs = referencias(documento);
        assertEquals(3, refs.size(), "El ANEXO 14 del SRI muestra 3 ds:Reference");

        boolean haySignedProperties = false;
        boolean hayComprobanteConEnveloped = false;
        boolean hayKeyInfo = false;

        String idKeyInfo = unico(documento, XadesSpikeSigner.NS_DSIG, "KeyInfo").getAttribute("Id");
        assertFalse(idKeyInfo.isBlank(), "ds:KeyInfo debe tener Id para poder referenciarlo");

        for (Element referencia : refs) {
            String tipo = referencia.getAttribute("Type");
            String uri = referencia.getAttribute("URI");

            if (XadesSpikeSigner.TYPE_SIGNED_PROPERTIES.equals(tipo)) {
                haySignedProperties = true;
            } else if (("#" + ID_COMPROBANTE).equals(uri)) {
                NodeList transforms = referencia
                        .getElementsByTagNameNS(XadesSpikeSigner.NS_DSIG, "Transform");
                boolean enveloped = false;
                for (int i = 0; i < transforms.getLength(); i++) {
                    if ("http://www.w3.org/2000/09/xmldsig#enveloped-signature"
                            .equals(((Element) transforms.item(i)).getAttribute("Algorithm"))) {
                        enveloped = true;
                    }
                }
                assertTrue(enveloped, "La referencia al comprobante necesita transform enveloped-signature");
                hayComprobanteConEnveloped = true;
            } else if (("#" + idKeyInfo).equals(uri)) {
                hayKeyInfo = true;
            }
        }

        assertTrue(haySignedProperties, "Falta la ds:Reference a SignedProperties");
        assertTrue(hayComprobanteConEnveloped, "Falta la ds:Reference a #" + ID_COMPROBANTE);
        assertTrue(hayKeyInfo, "Falta la ds:Reference a ds:KeyInfo (seccion 6.5 de la ficha)");
    }

    @Test
    @DisplayName("C2 - QualifyingProperties usa el namespace XAdES 1.3.2 y apunta a la firma")
    void estructura_qualifyingProperties() throws Exception {
        Document documento = XmlSeguro.parsear(
                firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1));

        Element qualifying = unico(documento, XadesSpikeSigner.NS_XADES_1_3_2, "QualifyingProperties");
        assertEquals(XadesSpikeSigner.NS_XADES_1_3_2, qualifying.getNamespaceURI());

        String idFirma = unico(documento, XadesSpikeSigner.NS_DSIG, "Signature").getAttribute("Id");
        assertFalse(idFirma.isBlank(), "ds:Signature debe tener Id");
        assertEquals("#" + idFirma, qualifying.getAttribute("Target"),
                "QualifyingProperties/@Target debe apuntar a la firma");
    }

    @Test
    @DisplayName("C3 - SignedProperties contiene SigningTime, SigningCertificate, CertDigest e IssuerSerial")
    void estructura_signedProperties() throws Exception {
        Document documento = XmlSeguro.parsear(
                firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1));

        String ns = XadesSpikeSigner.NS_XADES_1_3_2;
        Element signedProperties = unico(documento, ns, "SignedProperties");
        assertFalse(signedProperties.getAttribute("Id").isBlank(),
                "SignedProperties necesita Id para ser referenciada");

        unico(documento, ns, "SignedSignatureProperties");
        Element signingTime = unico(documento, ns, "SigningTime");
        assertFalse(signingTime.getTextContent().isBlank(), "SigningTime vacio");

        unico(documento, ns, "SigningCertificate");
        unico(documento, ns, "Cert");
        unico(documento, ns, "CertDigest");
        Element issuerSerial = unico(documento, ns, "IssuerSerial");

        // OJO: ds:X509IssuerName aparece DOS veces en el documento -dentro de
        // xades:IssuerSerial y dentro de ds:KeyInfo/ds:X509Data/ds:X509IssuerSerial,
        // porque includeIssuerSerial(true) alimenta ambos sitios. La busqueda se
        // acota al IssuerSerial de XAdES, que es el que exige el perfil BES.
        Element issuerName = (Element) issuerSerial
                .getElementsByTagNameNS(XadesSpikeSigner.NS_DSIG, "X509IssuerName").item(0);
        assertNotNull(issuerName, "xades:IssuerSerial debe contener ds:X509IssuerName");
        assertTrue(issuerName.getTextContent().contains("BIOPET XADES TEST"),
                "El IssuerSerial debe referirse al certificado de prueba");
        assertNotNull(issuerSerial
                        .getElementsByTagNameNS(XadesSpikeSigner.NS_DSIG, "X509SerialNumber").item(0),
                "xades:IssuerSerial debe contener ds:X509SerialNumber");

        // La referencia a SignedProperties debe apuntar exactamente a ese Id.
        boolean referenciada = referencias(documento).stream()
                .anyMatch(r -> ("#" + signedProperties.getAttribute("Id")).equals(r.getAttribute("URI")));
        assertTrue(referenciada, "Ninguna ds:Reference apunta al Id de SignedProperties");
    }

    @Test
    @DisplayName("C4 - KeyInfo lleva el certificado X509 en Base64 y la clave publica RSA")
    void estructura_keyInfo() throws Exception {
        Document documento = XmlSeguro.parsear(
                firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1));

        X509Certificate delXml = certificadoDeKeyInfo(documento);
        assertEquals(material.certificate(), delXml,
                "El certificado del KeyInfo debe ser el del PKCS#12 de prueba");

        unico(documento, XadesSpikeSigner.NS_DSIG, "RSAKeyValue");
        unico(documento, XadesSpikeSigner.NS_DSIG, "Modulus");
        assertEquals("AQAB",
                unico(documento, XadesSpikeSigner.NS_DSIG, "Exponent").getTextContent().trim());
    }

    @Test
    @DisplayName("C5 - Canonicalizacion C14N 1.0 y firma ENVELOPED dentro de <factura>")
    void estructura_canonicalizacionYEnveloped() throws Exception {
        Document documento = XmlSeguro.parsear(
                firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1));

        assertEquals(XadesSpikeSigner.C14N_1_0,
                unico(documento, XadesSpikeSigner.NS_DSIG, "CanonicalizationMethod")
                        .getAttribute("Algorithm"));

        Element firma = unico(documento, XadesSpikeSigner.NS_DSIG, "Signature");
        assertEquals("factura", firma.getParentNode().getNodeName(),
                "ENVELOPED: la firma debe colgar del propio comprobante");
        assertEquals("factura", documento.getDocumentElement().getNodeName());
        assertEquals(ID_COMPROBANTE, documento.getDocumentElement().getAttribute("id"));
    }

    @Test
    @DisplayName("C6 - El XML firmado se serializa en UTF-8")
    void estructura_utf8() throws Exception {
        String firmado = firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1);
        assertTrue(firmado.contains("UTF-8"), "La declaracion XML debe indicar UTF-8");
    }

    // ------------------------------------------------------------------
    // D - DONDE VIVE EXACTAMENTE LA RESTRICCION DE SHA-1
    // ------------------------------------------------------------------

    /**
     * Verifica la MISMA firma RSA-SHA1 con el proveedor XMLDSig del propio JDK
     * ({@code javax.xml.crypto.dsig}) en vez de con Apache Santuario.
     *
     * <p>Es el experimento que localiza la restriccion: el JDK aplica
     * {@code jdk.xml.dsig.secureValidationPolicy} de {@code java.security}, que
     * incluye SHA-1 entre los algoritmos prohibidos; Santuario tiene su propia
     * lista, distinta. Se pasa {@code secureValidation} como PROPIEDAD DEL
     * CONTEXTO DE VALIDACION, no como ajuste global del proceso.
     */
    private String verificarConProveedorDelJdk(String xmlFirmado, boolean validacionSegura) throws Exception {
        Document documento = XmlSeguro.parsear(xmlFirmado);
        XmlSeguro.registrarAtributosId(documento);

        Element firma = (Element) documento
                .getElementsByTagNameNS(XadesSpikeSigner.NS_DSIG, "Signature").item(0);

        javax.xml.crypto.dsig.dom.DOMValidateContext contexto =
                new javax.xml.crypto.dsig.dom.DOMValidateContext(
                        certificadoDeKeyInfo(documento).getPublicKey(), firma);
        contexto.setProperty("org.jcp.xml.dsig.secureValidation", validacionSegura);

        javax.xml.crypto.dsig.XMLSignatureFactory factoria =
                javax.xml.crypto.dsig.XMLSignatureFactory.getInstance("DOM");
        try {
            javax.xml.crypto.dsig.XMLSignature sig = factoria.unmarshalXMLSignature(contexto);
            return "validez=" + sig.validate(contexto);
        } catch (Exception ex) {
            return "EXCEPCION " + ex.getClass().getName() + ": " + ex.getMessage();
        }
    }

    @Test
    @DisplayName("D1 - RSA-SHA1 con el proveedor XMLDSig del JDK: donde aparece la restriccion")
    void experimentoD_proveedorDelJdkConSha1() throws Exception {
        String firmado = firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1);
        System.out.println("[SPIKE D1] JDK XMLDSig / RSA-SHA1 / secureValidation=true  -> "
                + verificarConProveedorDelJdk(firmado, true));
        System.out.println("[SPIKE D1] JDK XMLDSig / RSA-SHA1 / secureValidation=false -> "
                + verificarConProveedorDelJdk(firmado, false));
    }

    @Test
    @DisplayName("D2 - RSA-SHA256 con el proveedor XMLDSig del JDK: contraste")
    void experimentoD_proveedorDelJdkConSha256() throws Exception {
        String firmado = firmar(XadesSpikeSigner.SIG_RSA_SHA256, XadesSpikeSigner.DIGEST_SHA256);
        System.out.println("[SPIKE D2] JDK XMLDSig / RSA-SHA256 / secureValidation=true  -> "
                + verificarConProveedorDelJdk(firmado, true));
    }

    @Test
    @DisplayName("D3 - Volcado de la firma producida, para el informe")
    void volcadoEstructural() throws Exception {
        Document documento = XmlSeguro.parsear(
                firmar(XadesSpikeSigner.SIG_RSA_SHA1, XadesSpikeSigner.DIGEST_SHA1));

        System.out.println("[SPIKE D3] --- esqueleto de la firma producida ---");
        System.out.println("[SPIKE D3] SignatureMethod       = "
                + unico(documento, XadesSpikeSigner.NS_DSIG, "SignatureMethod").getAttribute("Algorithm"));
        System.out.println("[SPIKE D3] CanonicalizationMethod= "
                + unico(documento, XadesSpikeSigner.NS_DSIG, "CanonicalizationMethod").getAttribute("Algorithm"));
        for (Element referencia : referencias(documento)) {
            Element digest = (Element) referencia
                    .getElementsByTagNameNS(XadesSpikeSigner.NS_DSIG, "DigestMethod").item(0);
            System.out.println("[SPIKE D3] Reference URI=" + referencia.getAttribute("URI")
                    + " Type=" + (referencia.getAttribute("Type").isBlank() ? "(ninguno)" : referencia.getAttribute("Type"))
                    + " Digest=" + digest.getAttribute("Algorithm"));
        }
        System.out.println("[SPIKE D3] QualifyingProperties ns = "
                + unico(documento, XadesSpikeSigner.NS_XADES_1_3_2, "QualifyingProperties").getNamespaceURI());
        System.out.println("[SPIKE D3] SigningTime            = "
                + unico(documento, XadesSpikeSigner.NS_XADES_1_3_2, "SigningTime").getTextContent());
    }
}
