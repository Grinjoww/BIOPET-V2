package com.biopet.facturacion.sri.ws.autorizacion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Una autorizacion concreta.
 *
 * <h2>Por que fechaAutorizacion es String y no XMLGregorianCalendar</h2>
 *
 * <p>El WSDL la declara {@code xs:dateTime} y JAXB sabria mapearla sola. El
 * problema es el modo de fallo: si el SRI enviase un valor con un formato que
 * el parser estricto de JAXB no acepta, reventaria el UNMARSHALLING ENTERO y
 * BIOPET perderia tambien el estado y el numero de autorizacion de una factura
 * que quizas acaba de ser autorizada. Mapeandola como texto, un formato
 * inesperado degrada a fecha nula y el resto de la respuesta -que es lo
 * fiscalmente relevante- se persiste igual. La conversion se hace despues, de
 * forma defensiva.
 *
 * <p>{@code comprobante} trae el XML autorizado como texto escapado o en CDATA;
 * JAXB lo devuelve ya desescapado. Es la fuente de XML_AUTORIZADO.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class AutorizacionWs {

    @XmlElement(name = "estado")
    private String estado;

    @XmlElement(name = "numeroAutorizacion")
    private String numeroAutorizacion;

    @XmlElement(name = "fechaAutorizacion")
    private String fechaAutorizacion;

    @XmlElement(name = "ambiente")
    private String ambiente;

    @XmlElement(name = "comprobante")
    private String comprobante;

    @XmlElement(name = "mensajes")
    private MensajesWs mensajes;

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getNumeroAutorizacion() {
        return numeroAutorizacion;
    }

    public void setNumeroAutorizacion(String numeroAutorizacion) {
        this.numeroAutorizacion = numeroAutorizacion;
    }

    public String getFechaAutorizacion() {
        return fechaAutorizacion;
    }

    public void setFechaAutorizacion(String fechaAutorizacion) {
        this.fechaAutorizacion = fechaAutorizacion;
    }

    public String getAmbiente() {
        return ambiente;
    }

    public void setAmbiente(String ambiente) {
        this.ambiente = ambiente;
    }

    public String getComprobante() {
        return comprobante;
    }

    public void setComprobante(String comprobante) {
        this.comprobante = comprobante;
    }

    public MensajesWs getMensajes() {
        return mensajes;
    }

    public void setMensajes(MensajesWs mensajes) {
        this.mensajes = mensajes;
    }
}
