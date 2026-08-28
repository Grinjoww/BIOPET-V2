package com.biopet.facturacion.sri.ws.recepcion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuerpo de la respuesta de recepcion: {@code estado} (RECIBIDA | DEVUELTA) y,
 * cuando hay algo que decir, la lista de comprobantes con sus mensajes.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class RespuestaSolicitudWs {

    @XmlElement(name = "estado")
    private String estado;

    @XmlElement(name = "comprobantes")
    private ComprobantesWs comprobantes;

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public ComprobantesWs getComprobantes() {
        return comprobantes;
    }

    public void setComprobantes(ComprobantesWs comprobantes) {
        this.comprobantes = comprobantes;
    }

    /** Todos los mensajes de todos los comprobantes, sin perder ninguno. */
    public List<MensajeWs> todosLosMensajes() {
        List<MensajeWs> todos = new ArrayList<>();
        if (comprobantes == null || comprobantes.getComprobante() == null) {
            return todos;
        }
        for (ComprobanteWs comprobante : comprobantes.getComprobante()) {
            if (comprobante == null || comprobante.getMensajes() == null) continue;
            List<MensajeWs> mensajes = comprobante.getMensajes().getMensaje();
            if (mensajes != null) todos.addAll(mensajes);
        }
        return todos;
    }
}
