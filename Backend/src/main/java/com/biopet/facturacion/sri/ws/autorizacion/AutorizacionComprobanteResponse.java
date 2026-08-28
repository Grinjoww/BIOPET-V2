package com.biopet.facturacion.sri.ws.autorizacion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/** Respuesta de {@code autorizacionComprobante}. */
@XmlRootElement(name = "autorizacionComprobanteResponse", namespace = NamespaceAutorizacion.URI)
@XmlAccessorType(XmlAccessType.FIELD)
public class AutorizacionComprobanteResponse {

    @XmlElement(name = "RespuestaAutorizacionComprobante")
    private RespuestaComprobanteWs respuesta;

    public RespuestaComprobanteWs getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(RespuestaComprobanteWs respuesta) {
        this.respuesta = respuesta;
    }
}
