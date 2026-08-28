package com.biopet.facturacion.sri.ws.recepcion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.List;

/** Envoltorio {@code <mensajes>} con N {@code <mensaje>}. */
@XmlAccessorType(XmlAccessType.FIELD)
public class MensajesWs {

    /**
     * QUALIFIED por la misma razon que en {@code ComprobantesWs}: el WSDL lo
     * declara como {@code ref="tns:mensaje"}, una referencia a un elemento
     * global, y esos van siempre con el namespace del esquema.
     */
    @XmlElement(name = "mensaje", namespace = NamespaceRecepcion.URI)
    private List<MensajeWs> mensaje;

    public List<MensajeWs> getMensaje() {
        return mensaje;
    }

    public void setMensaje(List<MensajeWs> mensaje) {
        this.mensaje = mensaje;
    }
}
