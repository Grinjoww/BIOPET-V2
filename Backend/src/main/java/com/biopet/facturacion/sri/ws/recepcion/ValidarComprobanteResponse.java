package com.biopet.facturacion.sri.ws.recepcion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Respuesta de {@code validarComprobante}. El unico hijo se llama
 * {@code RespuestaRecepcionComprobante}, con mayuscula inicial, tal y como lo
 * publica el SRI; el {@code @XmlElement} explicito evita que JAXB lo derive del
 * nombre del campo Java y deje de encajar.
 */
@XmlRootElement(name = "validarComprobanteResponse", namespace = NamespaceRecepcion.URI)
@XmlAccessorType(XmlAccessType.FIELD)
public class ValidarComprobanteResponse {

    @XmlElement(name = "RespuestaRecepcionComprobante")
    private RespuestaSolicitudWs respuesta;

    public RespuestaSolicitudWs getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(RespuestaSolicitudWs respuesta) {
        this.respuesta = respuesta;
    }
}
