package com.biopet.facturacion.firma;

import com.biopet.facturacion.exception.FirmaElectronicaException;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Parseo y serializacion de XML endurecidos, para el modulo de firma.
 *
 * <p>Version productiva del helper que valido el spike de la Fase 3. Se
 * mantienen todas las defensas: DOCTYPE prohibido de raiz (lo que corta XXE,
 * DTD externo y bombas de entidades), entidades externas desactivadas, sin
 * resolucion externa de DTD ni de esquema, sin XInclude y con
 * {@code FEATURE_SECURE_PROCESSING} activo. Ninguna se relaja para que la firma
 * funcione.
 *
 * <p>{@code namespaceAware} es obligatorio, no opcional: la canonicalizacion
 * XML-DSig opera sobre nombres cualificados y sin esto la firma seria incorrecta.
 */
final class XmlFirmaSeguro {

    private XmlFirmaSeguro() {
    }

    static DocumentBuilderFactory factoriaEndurecida() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setValidating(false);
        factory.setNamespaceAware(true);

        return factory;
    }

    static Document parsear(byte[] xml) {
        try {
            DocumentBuilder builder = factoriaEndurecida().newDocumentBuilder();
            return builder.parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (Exception e) {
            throw new FirmaElectronicaException(
                    "El XML del comprobante no se pudo parsear de forma segura: " + e.getMessage(), e);
        }
    }

    static byte[] serializar(Document documento) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            // Sin sangrado: anadir espacios en blanco DESPUES de firmar romperia
            // la firma, porque forman parte de lo canonicalizado.
            transformer.setOutputProperty(OutputKeys.INDENT, "no");

            // Evita que el Transformer anada standalone="no" a la declaracion:
            // el comprobante debe conservar la misma cabecera que tenia antes de
            // firmarse.
            documento.setXmlStandalone(true);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(documento), new StreamResult(salida));
            return salida.toByteArray();
        } catch (Exception e) {
            throw new FirmaElectronicaException(
                    "No se pudo serializar el comprobante firmado: " + e.getMessage(), e);
        }
    }

    /**
     * Marca como atributos de tipo ID todos los {@code Id}/{@code id} del arbol.
     *
     * <p>Hace falta tras RE-PARSEAR un XML firmado: la informacion de "este
     * atributo es un ID" vive en el DTD/esquema y, al no cargar ninguno por
     * seguridad, el DOM la pierde. Sin esto Santuario no resuelve las URI
     * internas {@code #comprobante}, {@code #...-SignedProperties} ni
     * {@code #Certificate...} y la verificacion falla por un motivo estructural
     * que nada tiene que ver con la validez de la firma.
     *
     * <p>No relaja ninguna comprobacion criptografica: solo restituye
     * informacion estructural del DOM.
     */
    static void registrarAtributosId(Document documento) {
        registrarEnElemento(documento.getDocumentElement());
    }

    private static void registrarEnElemento(Element elemento) {
        NamedNodeMap atributos = elemento.getAttributes();
        for (int i = 0; i < atributos.getLength(); i++) {
            Attr atributo = (Attr) atributos.item(i);
            String nombre = atributo.getLocalName() != null
                    ? atributo.getLocalName() : atributo.getName();
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
