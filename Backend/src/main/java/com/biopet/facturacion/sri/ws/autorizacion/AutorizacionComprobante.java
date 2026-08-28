package com.biopet.facturacion.sri.ws.autorizacion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Peticion {@code autorizacionComprobante(claveAccesoComprobante)}.
 *
 * <p>La clave que viaja aqui es SIEMPRE la que quedo congelada en
 * {@code facturas.clave_acceso}. Nunca se recalcula: recalcularla en cada
 * consulta abriria la puerta a preguntar por un comprobante distinto del que se
 * envio.
 */
@XmlRootElement(name = "autorizacionComprobante", namespace = NamespaceAutorizacion.URI)
@XmlAccessorType(XmlAccessType.FIELD)
public class AutorizacionComprobante {

    @XmlElement(name = "claveAccesoComprobante")
    private String claveAccesoComprobante;

    public AutorizacionComprobante() {
    }

    public AutorizacionComprobante(String claveAccesoComprobante) {
        this.claveAccesoComprobante = claveAccesoComprobante;
    }

    public String getClaveAccesoComprobante() {
        return claveAccesoComprobante;
    }

    public void setClaveAccesoComprobante(String claveAccesoComprobante) {
        this.claveAccesoComprobante = claveAccesoComprobante;
    }
}
