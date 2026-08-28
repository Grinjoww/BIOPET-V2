package com.biopet.facturacion.firma;

import com.biopet.facturacion.domain.CalculoFacturaService;
import com.biopet.facturacion.exception.FirmaElectronicaException;
import com.biopet.facturacion.xml.FacturaXmlBuilder;
import com.biopet.facturacion.xml.FacturaXmlFixture;
import com.biopet.facturacion.xml.FacturaXsdValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Estructura y correccion criptografica de la firma XAdES-BES.
 *
 * <p>No basta con que xades4j produzca algo: se afirma sobre cada elemento que
 * el ANEXO 14 de la ficha del SRI muestra, porque un comprobante al que le falte
 * una referencia o traiga otro algoritmo lo rechaza el SRI, no el compilador.
 *
 * <p>El certificado es autofirmado y ficticio. Sirve para probar la mecanica;
 * NO permite afirmar nada sobre la aceptacion real del SRI.
 */
class FacturaXadesSignerTest {

    private static final String NS_DSIG = FacturaXadesSigner.NS_DSIG;
    private static final String NS_XADES = FacturaXadesSigner.NS_XADES_1_3_2;

    private final FacturaXadesSigner signer = new FacturaXadesSigner();
    private final FirmaXadesVerificador verificador = new FirmaXadesVerificador();
    private final FacturaXmlBuilder builder = new FacturaXmlBuilder(new CalculoFacturaService());
    private final FacturaXsdValidator xsdValidator = new FacturaXsdValidator();

    private static MaterialFirma material;

    @BeforeAll
    static void generarCertificado(@TempDir Path temporal) throws Exception {
        Path p12 = CertificadoPruebaFactory.valido(temporal.resolve("firma.p12"));
        FirmaProperties propiedades = new FirmaProperties();
        propiedades.getCertificado().setPath(p12.toString());
        propiedades.getCertificado().setPassword(CertificadoPruebaFactory.PASSWORD);
        material = new CertificadoFirmaProvider(propiedades).material();
    }

    private byte[] xmlGenerado() {
        return builder.construir(FacturaXmlFixture.facturaEmitida());
    }

    private byte[] firmar() {
        return signer.firmar(xmlGenerado(), material, AlgoritmoFirmaSri.RSA_SHA1);
    }

    private Document parsear(byte[] xml) {
        return XmlFirmaSeguro.parsear(xml);
    }

    private List<Element> elementos(Document doc, String ns, String nombre) {
        NodeList nodos = doc.getElementsByTagNameNS(ns, nombre);
        List<Element> lista = new ArrayList<>();
        for (int i = 0; i < nodos.getLength(); i++) {
            lista.add((Element) nodos.item(i));
        }
        return lista;
    }

    private Element unico(Document doc, String ns, String nombre) {
        List<Element> encontrados = elementos(doc, ns, nombre);
        assertThat(encontrados).as("se esperaba exactamente un <%s>", nombre).hasSize(1);
        return encontrados.get(0);
    }

    // ==================================================================
    // Estructura obligatoria
    // ==================================================================

    @Test
    void elComprobanteFirmadoConservaSuIdentidadYAnadeLaFirma() {
        String xml = new String(firmar(), StandardCharsets.UTF_8);

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("id=\"comprobante\"").contains("version=\"2.1.0\"");
        assertThat(xml).contains("Signature");
        // Los datos fiscales siguen intactos tras firmar.
        assertThat(xml).contains("<importeTotal>46.00</importeTotal>");
        assertThat(xml).contains("<claveAcceso>" + FacturaXmlFixture.claveAcceso() + "</claveAcceso>");
    }

    @Test
    void laFirmaUsaRsaSha1YC14N10() {
        Document doc = parsear(firmar());

        assertThat(unico(doc, NS_DSIG, "SignatureMethod").getAttribute("Algorithm"))
                .isEqualTo(AlgoritmoFirmaSri.RSA_SHA1.uriFirma());
        assertThat(unico(doc, NS_DSIG, "CanonicalizationMethod").getAttribute("Algorithm"))
                .isEqualTo(FacturaXadesSigner.C14N_1_0);
    }

    @Test
    void hayExactamenteTresReferenciasConLosTresDestinosDelAnexo() {
        Document doc = parsear(firmar());
        List<Element> referencias = elementos(doc, NS_DSIG, "Reference");

        assertThat(referencias).hasSize(3);

        boolean alComprobante = false;
        boolean aSignedProperties = false;
        boolean aKeyInfo = false;
        String idKeyInfo = unico(doc, NS_DSIG, "KeyInfo").getAttribute("Id");

        for (Element referencia : referencias) {
            String uri = referencia.getAttribute("URI");
            String tipo = referencia.getAttribute("Type");

            if (("#" + FacturaXmlBuilder.ID_COMPROBANTE).equals(uri)) {
                alComprobante = true;
                // La transform enveloped-signature es lo que permite firmar un
                // documento que va a contener la propia firma.
                assertThat(referencia.getElementsByTagNameNS(NS_DSIG, "Transform").getLength())
                        .isPositive();
            } else if (FacturaXadesSigner.TYPE_SIGNED_PROPERTIES.equals(tipo)) {
                aSignedProperties = true;
            } else if (uri.equals("#" + idKeyInfo)) {
                aKeyInfo = true;
            }
        }

        assertThat(alComprobante).as("referencia a #comprobante").isTrue();
        assertThat(aSignedProperties).as("referencia a SignedProperties con Type correcto").isTrue();
        // Esta es la que xades4j NO genera por defecto y la ficha si exige.
        assertThat(aKeyInfo).as("referencia a ds:KeyInfo").isTrue();
    }

    @Test
    void lasTresReferenciasUsanDigestSha1() {
        Document doc = parsear(firmar());

        List<Element> digests = new ArrayList<>();
        for (Element referencia : elementos(doc, NS_DSIG, "Reference")) {
            digests.add((Element) referencia.getElementsByTagNameNS(NS_DSIG, "DigestMethod").item(0));
        }

        assertThat(digests).hasSize(3);
        assertThat(digests).allSatisfy(digest ->
                assertThat(digest.getAttribute("Algorithm"))
                        .isEqualTo(AlgoritmoFirmaSri.RSA_SHA1.uriDigest()));
    }

    @Test
    void lasPropiedadesXadesEstanEnElNamespace132YApuntanALaFirma() {
        Document doc = parsear(firmar());

        Element qualifying = unico(doc, NS_XADES, "QualifyingProperties");
        assertThat(qualifying.getNamespaceURI()).isEqualTo(NS_XADES);

        // Target debe apuntar a la Signature de este mismo documento.
        String target = qualifying.getAttribute("Target");
        String idFirma = unico(doc, NS_DSIG, "Signature").getAttribute("Id");
        assertThat(target).isEqualTo("#" + idFirma);

        assertThat(elementos(doc, NS_XADES, "SignedProperties")).hasSize(1);
        assertThat(elementos(doc, NS_XADES, "SigningTime")).hasSize(1);
        assertThat(elementos(doc, NS_XADES, "SigningCertificate")).hasSize(1);
        assertThat(elementos(doc, NS_XADES, "CertDigest")).hasSize(1);
        assertThat(elementos(doc, NS_XADES, "IssuerSerial")).hasSize(1);
        assertThat(unico(doc, NS_XADES, "SigningTime").getTextContent()).isNotBlank();
    }

    @Test
    void keyInfoPublicaElCertificadoYLaClaveRsa() {
        Document doc = parsear(firmar());

        assertThat(elementos(doc, NS_DSIG, "KeyInfo")).hasSize(1);
        assertThat(elementos(doc, NS_DSIG, "X509Certificate")).hasSize(1);
        assertThat(elementos(doc, NS_DSIG, "RSAKeyValue")).hasSize(1);
        assertThat(unico(doc, NS_DSIG, "X509Certificate").getTextContent()).isNotBlank();
    }

    // ==================================================================
    // Verificacion criptografica
    // ==================================================================

    @Test
    void laFirmaRecienProducidaVerifica() {
        assertThat(verificador.esValida(firmar())).isTrue();
    }

    @Test
    void alterarUnImporteFirmadoInvalidaLaFirma() {
        byte[] firmado = firmar();
        assertThat(verificador.esValida(firmado)).as("precondicion").isTrue();

        String alterado = new String(firmado, StandardCharsets.UTF_8)
                .replace("<importeTotal>46.00</importeTotal>",
                        "<importeTotal>46.01</importeTotal>");

        assertThat(verificador.esValida(alterado.getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void alterarElCompradorInvalidaLaFirma() {
        byte[] firmado = firmar();

        String alterado = new String(firmado, StandardCharsets.UTF_8)
                .replace("<razonSocialComprador>MARIA LOPEZ</razonSocialComprador>",
                        "<razonSocialComprador>OTRA PERSONA</razonSocialComprador>");

        assertThat(verificador.esValida(alterado.getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void sustituirElCertificadoDeKeyInfoInvalidaLaFirma() throws Exception {
        // Esta es la razon de ser de la TERCERA referencia. Sin firmar KeyInfo,
        // cambiar el certificado publicado no rompería nada y un tercero podria
        // presentar el comprobante como firmado por otro.
        byte[] firmado = firmar();
        assertThat(verificador.esValida(firmado)).as("precondicion").isTrue();

        Path otro = Files.createTempDirectory("otro-cert").resolve("otro.p12");
        CertificadoPruebaFactory.valido(otro);
        FirmaProperties propiedades = new FirmaProperties();
        propiedades.getCertificado().setPath(otro.toString());
        propiedades.getCertificado().setPassword(CertificadoPruebaFactory.PASSWORD);
        MaterialFirma otroMaterial = new CertificadoFirmaProvider(propiedades).material();

        // La sustitucion se hace sobre el DOM y no con un replace de texto: el
        // base64 del certificado va partido en lineas y el serializador escapa
        // los retornos de carro como &#13;, asi que buscar la cadena tal cual
        // sale de getTextContent() no encontraria nada y el test pasaria en
        // falso sin haber alterado el documento.
        Document doc = parsear(firmado);
        Element x509 = unico(doc, NS_DSIG, "X509Certificate");
        String certificadoOriginal = x509.getTextContent();
        x509.setTextContent(java.util.Base64.getMimeEncoder()
                .encodeToString(otroMaterial.certificate().getEncoded()));
        assertThat(x509.getTextContent()).isNotEqualTo(certificadoOriginal);

        byte[] alterado = XmlFirmaSeguro.serializar(doc);
        assertThat(alterado).isNotEqualTo(firmado);

        assertThat(verificador.esValida(alterado)).isFalse();
    }

    @Test
    void unDocumentoSinFirmaNoVerifica() {
        assertThat(verificador.esValida(xmlGenerado())).isFalse();
        assertThat(verificador.esValida(null)).isFalse();
        assertThat(verificador.esValida(new byte[0])).isFalse();
    }

    // ==================================================================
    // XSD despues de firmar
    // ==================================================================

    @Test
    void elComprobanteFirmadoSigueValidandoContraElXsdOficial() {
        // La firma va DENTRO de <factura>, asi que podria romper el esquema. Se
        // reutiliza el mismo validador de la Fase 5B; no hay un segundo.
        assertThatCode(() -> xsdValidator.validar(firmar())).doesNotThrowAnyException();
    }

    // ==================================================================
    // Entradas que no se deben firmar
    // ==================================================================

    @Test
    void noSeFirmaDosVecesElMismoComprobante() {
        byte[] firmado = firmar();

        assertThatThrownBy(() -> signer.firmar(firmado, material, AlgoritmoFirmaSri.RSA_SHA1))
                .isInstanceOf(FirmaElectronicaException.class)
                .hasMessageContaining("ya contiene una firma");
    }

    @Test
    void unDocumentoQueNoEsUnaFacturaNoSeFirma() {
        byte[] otro = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><otroDoc id=\"comprobante\"/>"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> signer.firmar(otro, material, AlgoritmoFirmaSri.RSA_SHA1))
                .isInstanceOf(FirmaElectronicaException.class)
                .hasMessageContaining("no es una factura");
    }

    @Test
    void unaFacturaSinIdComprobanteNoSeFirma() {
        byte[] sinId = new String(xmlGenerado(), StandardCharsets.UTF_8)
                .replace("id=\"comprobante\"", "id=\"otro\"")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> signer.firmar(sinId, material, AlgoritmoFirmaSri.RSA_SHA1))
                .isInstanceOf(FirmaElectronicaException.class)
                .hasMessageContaining("comprobante");
    }

    @Test
    void argumentosAusentesSeRechazan() {
        assertThatThrownBy(() -> signer.firmar(null, material, AlgoritmoFirmaSri.RSA_SHA1))
                .isInstanceOf(FirmaElectronicaException.class);
        assertThatThrownBy(() -> signer.firmar(xmlGenerado(), null, AlgoritmoFirmaSri.RSA_SHA1))
                .isInstanceOf(FirmaElectronicaException.class);
        assertThatThrownBy(() -> signer.firmar(xmlGenerado(), material, null))
                .isInstanceOf(FirmaElectronicaException.class);
    }

    @Test
    void unDocumentoConDoctypeNoSeFirma() {
        byte[] conDoctype = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE factura>"
                + "<factura id=\"comprobante\" version=\"2.1.0\"/>").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> signer.firmar(conDoctype, material, AlgoritmoFirmaSri.RSA_SHA1))
                .isInstanceOf(FirmaElectronicaException.class);
    }

    // ==================================================================
    // RSA-SHA256 (soportado, NO por defecto)
    // ==================================================================

    @Test
    void rsaSha256TambienSeProduceYVerifica() {
        // Que BIOPET pueda generarlo no significa que el SRI lo acepte: por eso
        // RSA_SHA1 sigue siendo el valor por defecto.
        byte[] firmado = signer.firmar(xmlGenerado(), material, AlgoritmoFirmaSri.RSA_SHA256);
        Document doc = parsear(firmado);

        assertThat(unico(doc, NS_DSIG, "SignatureMethod").getAttribute("Algorithm"))
                .isEqualTo(AlgoritmoFirmaSri.RSA_SHA256.uriFirma());
        assertThat(verificador.esValida(firmado)).isTrue();
        assertThatCode(() -> xsdValidator.validar(firmado)).doesNotThrowAnyException();
        assertThat(new FirmaProperties().getAlgoritmo()).isEqualTo(AlgoritmoFirmaSri.RSA_SHA1);
    }
}
