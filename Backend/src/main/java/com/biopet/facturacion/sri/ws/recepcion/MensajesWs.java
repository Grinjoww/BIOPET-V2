package com.biopet.facturacion.sri.ws.recepcion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;

import java.util.List;

/**
 * Envoltorio <mensajes> con N <mensaje>.
 *
 * Acepta tanto la variante cualificada contemplada por el contrato como
 * la variante sin namespace observada en CELCER.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class MensajesWs {

    @XmlElements({
            @XmlElement(
                    name = "mensaje",
                    namespace = NamespaceRecepcion.URI,
                    type = MensajeWs.class),
            @XmlElement(
                    name = "mensaje",
                    namespace = "",
                    type = MensajeWs.class)
    })
    private List<MensajeWs> mensaje;

    public List<MensajeWs> getMensaje() {
        return mensaje;
    }

    public void setMensaje(List<MensajeWs> mensaje) {
        this.mensaje = mensaje;
    }
}