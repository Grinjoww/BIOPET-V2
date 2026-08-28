package com.biopet.facturacion.sri.ws.autorizacion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.List;

/**
 * Cuerpo de la respuesta de autorizacion.
 *
 * <p>{@code numeroComprobantes} = 0 con {@code autorizaciones} vacio es la
 * respuesta que da el SRI cuando la clave todavia no le consta procesada. No es
 * un error: es el caso pendiente, y el orquestador lo trata como PPR.
 *
 * <p>{@code numeroComprobantes} se mapea como texto por el mismo motivo que la
 * fecha en {@link AutorizacionWs}: un valor inesperado no debe tumbar el
 * unmarshalling de una respuesta que quizas contiene una autorizacion valida.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class RespuestaComprobanteWs {

    @XmlElement(name = "claveAccesoConsultada")
    private String claveAccesoConsultada;

    @XmlElement(name = "numeroComprobantes")
    private String numeroComprobantes;

    @XmlElement(name = "autorizaciones")
    private AutorizacionesWs autorizaciones;

    public String getClaveAccesoConsultada() {
        return claveAccesoConsultada;
    }

    public void setClaveAccesoConsultada(String claveAccesoConsultada) {
        this.claveAccesoConsultada = claveAccesoConsultada;
    }

    public String getNumeroComprobantes() {
        return numeroComprobantes;
    }

    public void setNumeroComprobantes(String numeroComprobantes) {
        this.numeroComprobantes = numeroComprobantes;
    }

    public AutorizacionesWs getAutorizaciones() {
        return autorizaciones;
    }

    public void setAutorizaciones(AutorizacionesWs autorizaciones) {
        this.autorizaciones = autorizaciones;
    }

    /** Lista nunca nula de autorizaciones devueltas. */
    public List<AutorizacionWs> lista() {
        if (autorizaciones == null || autorizaciones.getAutorizacion() == null) {
            return List.of();
        }
        return autorizaciones.getAutorizacion();
    }
}
