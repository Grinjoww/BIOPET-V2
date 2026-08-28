package com.biopet.facturacion.sri.ws.recepcion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Mensaje funcional del SRI. Los cuatro campos se mapean, incluidos
 * {@code informacionAdicional} y {@code tipo}, que son opcionales: perder el
 * detalle de por que un comprobante fue devuelto convierte un diagnostico de
 * cinco minutos en uno de dos horas.
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
