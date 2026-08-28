package com.biopet.facturacion.sri.ws.recepcion;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.List;

/** Envoltorio {@code <comprobantes>} con N {@code <comprobante>}. */
@XmlAccessorType(XmlAccessType.FIELD)
public class ComprobantesWs {

    /**
     * QUALIFIED, aunque el esquema sea {@code elementFormDefault="unqualified"}.
     *
     * <p>No es una inconsistencia: el WSDL declara este hijo como
     * {@code <xs:element ref="tns:comprobante"/>}, es decir, una REFERENCIA a un
     * elemento GLOBAL. Los elementos globales llevan siempre el namespace del
     * esquema, y {@code elementFormDefault} solo gobierna a los locales. La
     * respuesta real del SRI mezcla ambas formas: {@code <estado>} y
     * {@code <comprobantes>} van sin prefijo, y {@code <ns2:comprobante>} con el.
     *
     * <p>Equivocarse aqui no da error: JAXB simplemente no encuentra el elemento
     * y deja la lista vacia. El sintoma seria una DEVUELTA sin ningun mensaje
     * -perdiendo justo el diagnostico por el que se devolvio- sin que nada
     * fallase. Ver {@code SriBindingContraWsdlTest}.
     */
    @XmlElement(name = "comprobante", namespace = NamespaceRecepcion.URI)
    private List<ComprobanteWs> comprobante;

    public List<ComprobanteWs> getComprobante() {
        return comprobante;
    }

    public void setComprobante(List<ComprobanteWs> comprobante) {
        this.comprobante = comprobante;
    }
}
