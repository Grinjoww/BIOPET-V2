package com.biopet.facturacion.sri.ws.autorizacion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Mensaje funcional del SRI en la respuesta de autorizacion. Clase gemela de la
 * de recepcion: se duplica porque el namespace en JAXB lo fija el paquete, y
 * estos dos servicios estan en namespaces distintos.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class MensajeWs {

    @XmlElement(name = "identificador")
    private String identificador;

    @XmlElement(name = "mensaje")
    private String mensaje;

    @XmlElement(name = "informacionAdicional")
    private String informacionAdicional;

    @XmlElement(name = "tipo")
    private String tipo;

    public String getIdentificador() {
        return identificador;
    }

    public void setIdentificador(String identificador) {
        this.identificador = identificador;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getInformacionAdicional() {
        return informacionAdicional;
    }

    public void setInformacionAdicional(String informacionAdicional) {
        this.informacionAdicional = informacionAdicional;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
