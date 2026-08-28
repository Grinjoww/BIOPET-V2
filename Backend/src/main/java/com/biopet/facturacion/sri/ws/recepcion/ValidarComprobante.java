package com.biopet.facturacion.sri.ws.recepcion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Peticion {@code validarComprobante(xml byte[])}.
 *
 * <p>El campo es {@code byte[]}, que JAXB serializa como {@code xs:base64Binary}
 * exactamente como declara el WSDL. Se le entregan los bytes del XML_FIRMADO
 * TAL CUAL estan en {@code factura_documentos}: no se reserializa, no se
 * reindenta y no se cambia el encoding. Cualquiera de esas cosas invalidaria la
 * firma XAdES, que cubre la forma canonica del documento firmado.
 */
@XmlRootElement(name = "validarComprobante", namespace = NamespaceRecepcion.URI)
@XmlAccessorType(XmlAccessType.FIELD)
public class ValidarComprobante {

    @XmlElement(name = "xml")
    private byte[] xml;

    public ValidarComprobante() {
    }

    public ValidarComprobante(byte[] xml) {
        this.xml = xml;
    }

    public byte[] getXml() {
        return xml;
    }

    public void setXml(byte[] xml) {
        this.xml = xml;
    }
}
