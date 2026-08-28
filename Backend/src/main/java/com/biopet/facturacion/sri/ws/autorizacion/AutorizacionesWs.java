package com.biopet.facturacion.sri.ws.autorizacion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.List;

/** Envoltorio de autorizaciones con N elementos {@code autorizacion}. */
@XmlAccessorType(XmlAccessType.FIELD)
public class AutorizacionesWs {

    /**
     * QUALIFIED: el WSDL lo declara como {@code ref="tns:autorizacion"}, una
     * referencia a un elemento GLOBAL, y esos van siempre con el namespace del
     * esquema aunque {@code elementFormDefault} sea {@code unqualified}.
     *
     * <p>Es el campo mas peligroso de todo el binding. Declararlo sin namespace
     * no produce ningun error: la lista llega vacia, y una lista vacia se
     * interpreta -correctamente, segun el contrato- como "el SRI aun no ha
     * resuelto", o sea PPR. El resultado seria que NINGUNA factura llegaria
     * nunca a AUTORIZADA, y el sistema no daria ni un solo aviso.
     */
    @XmlElement(name = "autorizacion", namespace = NamespaceAutorizacion.URI)
    private List<AutorizacionWs> autorizacion;

    public List<AutorizacionWs> getAutorizacion() {
        return autorizacion;
    }

    public void setAutorizacion(List<AutorizacionWs> autorizacion) {
        this.autorizacion = autorizacion;
    }
}
