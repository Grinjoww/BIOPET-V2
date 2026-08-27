package com.biopet.facturacion.spike;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/**
 * SPIKE FASE 3 - Helper de parseo/serializacion XML endurecido.
 *
 * <p>Todo el modulo de firma manipula XML de origen semi-confiable, asi que el
 * parser se configura contra XXE antes de tocar nada: se desactivan DTD,
 * entidades generales y de parametro externas, XInclude y expansion de
 * entidades, y se activa FEATURE_SECURE_PROCESSING junto con ACCESS_EXTERNAL_*
 * en vacio (ninguna resolucion externa permitida).
 *
 * <p>Vive en src/test porque esta fase es un spike: si se aprueba, este helper
 * es candidato a promoverse a produccion tal cual.
 */
final class XmlSeguro {

    private XmlSeguro() {
    }

    /** DocumentBuilderFactory endurecido contra XXE y resolucion externa. */
    static DocumentBuilderFactory factoriaEndurecida() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // Prohibe cualquier DOCTYPE: corta de raiz XXE y billion-laughs.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setValidating(false);

        // Obligatorio para firmar/verificar: la canonicalizacion XML-DSig opera
        // sobre nombres cualificados.
        factory.setNamespaceAware(true);

        return factory;
    }

    static Document parsear(String xml) throws Exception {
        DocumentBuilder builder = factoriaEndurecida().newDocumentBuilder();
        // Sin EntityResolver permisivo: si algo intentara resolver, falla.
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    static String serializar(Document documento) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(documento), new StreamResult(salida));
        return salida.toString(StandardCharsets.UTF_8);
    }

    /**
     * Marca como atributos de tipo ID todos los {@code Id}/{@code id} del arbol.
     *
     * <p>Necesario tras RE-PARSEAR un XML firmado: la informacion de "este
     * atributo es un ID" vive en el esquema/DTD, y al no usar ninguno (por
     * seguridad, ver arriba) el DOM la pierde. Sin esto, Santuario no puede
     * resolver las URI internas {@code #comprobante},
     * {@code #...-SignedProperties} ni {@code #Certificate...} al verificar.
     *
     * <p>No es un atajo de seguridad: no relaja ninguna validacion criptografica,
     * solo restituye informacion estructural del DOM.
     */
    static void registrarAtributosId(Document documento) {
        registrarEnElemento(documento.getDocumentElement());
    }

    private static void registrarEnElemento(Element elemento) {
        NamedNodeMap atributos = elemento.getAttributes();
        for (int i = 0; i < atributos.getLength(); i++) {
            Attr atributo = (Attr) atributos.item(i);
            String nombre = atributo.getLocalName() != null ? atributo.getLocalName() : atributo.getName();
            if ("Id".equals(nombre) || "id".equals(nombre)) {
                elemento.setIdAttributeNode(atributo, true);
            }
        }
        NodeList hijos = elemento.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            Node hijo = hijos.item(i);
            if (hijo.getNodeType() == Node.ELEMENT_NODE) {
                registrarEnElemento((Element) hijo);
            }
        }
    }
}
