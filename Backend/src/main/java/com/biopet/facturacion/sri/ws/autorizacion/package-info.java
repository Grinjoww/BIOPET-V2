/**
 * Binding JAXB del servicio {@code AutorizacionComprobantesOffline} del SRI.
 *
 * <p>Mismo criterio que el paquete de recepcion (ver su {@code package-info}):
 * bindings escritos a mano, tipados, con {@code elementFormDefault} sin
 * cualificar porque asi lo publica JAX-WS en el lado del SRI. Vive en su propio
 * paquete porque el namespace en JAXB es una propiedad del paquete, y este es
 * {@code http://ec.gob.sri.ws.autorizacion}.
 *
 * <p>Y la misma excepcion: {@code autorizacion} y {@code mensaje} se declaran
 * en el esquema como elementos GLOBALES referenciados con {@code ref="tns:..."},
 * asi que en la respuesta van CUALIFICADOS pese al {@code unqualified} del
 * esquema. {@code comprobante}, en cambio, es un elemento local de tipo
 * {@code xs:string} -el XML autorizado- y va sin namespace. Cada campo lo
 * documenta en su clase, y {@code SriBindingContraWsdlTest} lo comprueba contra
 * el WSDL oficial versionado.
 */
@jakarta.xml.bind.annotation.XmlSchema(
        namespace = com.biopet.facturacion.sri.ws.autorizacion.NamespaceAutorizacion.URI,
        elementFormDefault = jakarta.xml.bind.annotation.XmlNsForm.UNQUALIFIED)
package com.biopet.facturacion.sri.ws.autorizacion;
