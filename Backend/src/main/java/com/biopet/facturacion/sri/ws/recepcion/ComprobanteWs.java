package com.biopet.facturacion.sri.ws.recepcion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

/** Un comprobante dentro de la respuesta de recepcion, con sus mensajes. */
@XmlAccessorType(XmlAccessType.FIELD)
public class ComprobanteWs {

    @XmlElement(name = "claveAcceso")
    private String claveAcceso;

    @XmlElement(name = "mensajes")
    private MensajesWs mensajes;

    public String getClaveAcceso() {
        return claveAcceso;
    }

    public void setClaveAcceso(String claveAcceso) {
        this.claveAcceso = claveAcceso;
    }

    public MensajesWs getMensajes() {
        return mensajes;
    }

    public void setMensajes(MensajesWs mensajes) {
        this.mensajes = mensajes;
    }
}
