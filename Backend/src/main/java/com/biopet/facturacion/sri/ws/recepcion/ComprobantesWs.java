package com.biopet.facturacion.sri.ws.recepcion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;

import java.util.List;

/**
 * Envoltorio <comprobantes> con N <comprobante>.
 *
 * Acepta tanto la variante cualificada contemplada por el contrato como
 * la variante sin namespace observada en CELCER.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ComprobantesWs {

    @XmlElements({
            @XmlElement(
                    name = "comprobante",
                    namespace = NamespaceRecepcion.URI,
                    type = ComprobanteWs.class),
            @XmlElement(
                    name = "comprobante",
                    namespace = "",
                    type = ComprobanteWs.class)
    })
    private List<ComprobanteWs> comprobante;

    public List<ComprobanteWs> getComprobante() {
        return comprobante;
    }

    public void setComprobante(List<ComprobanteWs> comprobante) {
        this.comprobante = comprobante;
    }
}