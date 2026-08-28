package com.biopet.facturacion.firma;

import com.biopet.facturacion.domain.CalculoFacturaService;
import com.biopet.facturacion.xml.FacturaXmlBuilder;
import com.biopet.facturacion.xml.FacturaXmlFixture;
import org.apache.xml.security.signature.XMLSignature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BIOPET verifica firmas con la validacion segura de Apache Santuario ACTIVADA,
 * incluido el perfil RSA-SHA1 que exige el SRI.
 *
 * <p>Esta clase existe porque en la primera version de la Fase 6 se desactivo
 * {@code secureValidation} para RSA-SHA1 partiendo de una premisa equivocada: es
 * el proveedor XMLDSig del JDK ({@code javax.xml.crypto.dsig}) el que bloquea
 * SHA-1, no Santuario. Como aqui se usa Santuario -la misma implementacion que
 * xades4j emplea para firmar-, la relajacion nunca hizo falta.
 *
 * <p>Se prueban las dos mitades: que la proteccion admite RSA-SHA1 de verdad, y
 * que sigue detectando manipulaciones con esa proteccion puesta. Una validacion
 * segura que aceptase cualquier cosa tambien pasaria el primer test.
 */
class ValidacionSeguraFirmaTest {

    private static MaterialFirma material;

    private final FacturaXadesSigner signer = new FacturaXadesSigner();
    private final FirmaXadesVerificador verificador = new FirmaXadesVerificador();
    private final FacturaXmlBuilder builder = new FacturaXmlBuilder(new CalculoFacturaService());

    @BeforeAll
    static void certificado(@TempDir Path temporal) throws Exception {
        Path p12 = CertificadoPruebaFactory.valido(temporal.resolve("firma.p12"));
        FirmaProperties propiedades = new FirmaProperties();
        propiedades.getCertificado().setPath(p12.toString());
        propiedades.getCertificado().setPassword(CertificadoPruebaFactory.PASSWORD);
        material = new CertificadoFirmaProvider(propiedades).material();
    }

    private byte[] firmar(AlgoritmoFirmaSri algoritmo) {
        return signer.firmar(builder.construir(FacturaXmlFixture.facturaEmitida()), material, algoritmo);
    }

    /**
     * Verificacion con {@code secureValidation=true} explicito, sin pasar por el
     * verificador de BIOPET: demuestra el comportamiento de Santuario en si.
     */
    private boolean verificarConValidacionSegura(byte[] xmlFirmado) throws Exception {
        org.apache.xml.security.Init.init();
        Document documento = XmlFirmaSeguro.parsear(xmlFirmado);
        XmlFirmaSeguro.registrarAtributosId(documento);

        Element firma = (Element) documento
                .getElementsByTagNameNS(FacturaXadesSigner.NS_DSIG, "Signature").item(0);
        Element x509 = (Element) documento
                .getElementsByTagNameNS(FacturaXadesSigner.NS_DSIG, "X509Certificate").item(0);
        X509Certificate certificado = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(
                        Base64.getMimeDecoder().decode(x509.getTextContent().trim())));

        // true = validacion segura ACTIVADA.
        return new XMLSignature(firma, "", true).checkSignatureValue(certificado);
    }

    // ==================================================================
    // Santuario acepta RSA-SHA1 con la proteccion puesta
    // ==================================================================

    @Test
    void santuarioVerificaRsaSha1ConValidacionSeguraActivada() throws Exception {
        byte[] firmado = firmar(AlgoritmoFirmaSri.RSA_SHA1);

        assertThat(new String(firmado, StandardCharsets.UTF_8))
                .as("precondicion: la firma debe ser RSA-SHA1")
                .contains(AlgoritmoFirmaSri.RSA_SHA1.uriFirma());
        assertThat(verificarConValidacionSegura(firmado))
                .as("Santuario 4.0.4 admite RSA-SHA1 sin relajar nada")
                .isTrue();
    }

    @Test
    void elVerificadorDeBiopetValidaRsaSha1YRsaSha256() {
        assertThat(verificador.esValida(firmar(AlgoritmoFirmaSri.RSA_SHA1))).isTrue();
        assertThat(verificador.esValida(firmar(AlgoritmoFirmaSri.RSA_SHA256))).isTrue();
    }

    // ==================================================================
    // ...y sigue detectando manipulaciones
    // ==================================================================

    @Test
    void conValidacionSeguraSiguenDetectandoseLasManipulaciones() throws Exception {
        byte[] firmado = firmar(AlgoritmoFirmaSri.RSA_SHA1);

        String importeAlterado = new String(firmado, StandardCharsets.UTF_8)
                .replace("<importeTotal>46.00</importeTotal>", "<importeTotal>46.01</importeTotal>");
        assertThat(verificador.esValida(importeAlterado.getBytes(StandardCharsets.UTF_8))).isFalse();

        String compradorAlterado = new String(firmado, StandardCharsets.UTF_8)
                .replace("<razonSocialComprador>MARIA LOPEZ</razonSocialComprador>",
                        "<razonSocialComprador>OTRA PERSONA</razonSocialComprador>");
        assertThat(verificador.esValida(compradorAlterado.getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void conValidacionSeguraSustituirElCertificadoSigueInvalidandoLaFirma(@TempDir Path temporal)
            throws Exception {
        byte[] firmado = firmar(AlgoritmoFirmaSri.RSA_SHA1);

        Path otroP12 = CertificadoPruebaFactory.valido(temporal.resolve("otro.p12"));
        FirmaProperties propiedades = new FirmaProperties();
        propiedades.getCertificado().setPath(otroP12.toString());
        propiedades.getCertificado().setPassword(CertificadoPruebaFactory.PASSWORD);
        MaterialFirma otro = new CertificadoFirmaProvider(propiedades).material();

        // Sustitucion sobre el DOM: el base64 va partido en lineas y el
        // serializador escapa los retornos como &#13;, asi que un replace de
        // texto no encontraria nada y el test pasaria en falso.
        Document documento = XmlFirmaSeguro.parsear(firmado);
        Element x509 = (Element) documento
                .getElementsByTagNameNS(FacturaXadesSigner.NS_DSIG, "X509Certificate").item(0);
        x509.setTextContent(Base64.getMimeEncoder().encodeToString(otro.certificate().getEncoded()));
        byte[] alterado = XmlFirmaSeguro.serializar(documento);

        assertThat(alterado).isNotEqualTo(firmado);
        assertThat(verificador.esValida(alterado)).isFalse();
    }

    // ==================================================================
    // Ninguna relajacion en todo src/main
    // ==================================================================

    @Test
    void enSrcMainNoExisteNingunaRelajacionDeSeguridadXml() throws Exception {
        // Barrido del codigo productivo entero, no solo del modulo de firma:
        // una relajacion metida en cualquier otro sitio tendria el mismo efecto.
        // Se buscan tokens que SOLO aparecen al relajar algo. Deliberadamente no
        // se busca "java.security" a secas: es el prefijo de importaciones
        // legitimas (SecureRandom, KeyStore, X509Certificate...) y marcarlo daria
        // un test que grita sin motivo. Lo que si relajaria la politica es
        // Security.setProperty, que si esta en la lista.
        List<String> prohibidos = List.of(
                "secureValidation",
                "setSecureValidation",
                "org.jcp.xml.dsig",
                "jdk.xml.dsig",
                "System.setProperty",
                "Security.setProperty",
                "Security.removeProvider",
                "Security.insertProviderAt");

        List<String> hallazgos = new ArrayList<>();
        Path raiz = Path.of("src", "main");

        try (Stream<Path> archivos = Files.walk(raiz)) {
            for (Path archivo : archivos.filter(Files::isRegularFile).toList()) {
                String nombre = archivo.getFileName().toString();
                if (!nombre.endsWith(".java") && !nombre.endsWith(".yml")
                        && !nombre.endsWith(".yaml") && !nombre.endsWith(".properties")) {
                    continue;
                }
                String contenido = Files.readString(archivo, StandardCharsets.UTF_8);
                for (String linea : contenido.split("\n")) {
                    String limpia = linea.strip();
                    // Los comentarios explican por que NO se hace: no cuentan.
                    if (limpia.startsWith("//") || limpia.startsWith("*") || limpia.startsWith("/*")
                            || limpia.startsWith("#")) {
                        continue;
                    }
                    for (String prohibido : prohibidos) {
                        if (limpia.contains(prohibido)) {
                            hallazgos.add(archivo + " -> " + limpia);
                        }
                    }
                }
            }
        }

        assertThat(hallazgos)
                .as("codigo productivo que relajaria la validacion XML/XMLDSig")
                .isEmpty();
    }

    @Test
    void elModuloDeFirmaMantieneLasDefensasContraXxe() throws Exception {
        // Bloquear DOCTYPE corta XXE, DTD externo y bombas de entidades. Se
        // comprueba sobre el propio parser del modulo de firma.
        byte[] conDoctype = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE factura>"
                + "<factura id=\"comprobante\" version=\"2.1.0\"/>").getBytes(StandardCharsets.UTF_8);
        assertThat(verificador.esValida(conDoctype)).isFalse();

        String fuente = Files.readString(
                Path.of("src", "main", "java", "com", "biopet", "facturacion", "firma",
                        "XmlFirmaSeguro.java"), StandardCharsets.UTF_8);
        assertThat(fuente)
                .contains("disallow-doctype-decl")
                .contains("external-general-entities")
                .contains("external-parameter-entities")
                .contains("FEATURE_SECURE_PROCESSING")
                .contains("setXIncludeAware(false)");
    }
}
