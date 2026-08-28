/**
 * Binding JAXB del servicio {@code RecepcionComprobantesOffline} del SRI.
 *
 * <h2>Por que estas clases estan escritas a mano y no generadas</h2>
 *
 * <p>Se valoro generar los bindings con {@code jaxb2-maven-plugin} a partir del
 * WSDL. Se descarto por dos razones concretas:
 *
 * <ul>
 *   <li>el WSDL del SRI se publica en su servidor, y engancharlo al build
 *       significaria que compilar BIOPET dependa de que CELCER este arriba;
 *       versionar una copia local del WSDL solo para generar seis clases de
 *       datos anade un paso de build y un artefacto duplicado a cambio de
 *       nada;</li>
 *   <li>el contrato son dos operaciones y un punado de campos. Escribirlo a
 *       mano cabe en una pantalla, se lee, y deja documentar en el propio
 *       codigo cada decision de namespace.</li>
 * </ul>
 *
 * <p>Lo importante -y lo que exigia la fase- es que el cliente es TIPADO y el
 * SOAP lo serializa JAXB: en ningun punto del modulo se concatena un String
 * para formar un sobre SOAP.
 *
 * <h2>{@code elementFormDefault = UNQUALIFIED}</h2>
 *
 * <p>El servicio del SRI esta publicado con JAX-WS, cuyo esquema por defecto es
 * {@code unqualified}. Esto es observable en el propio dialogo: el elemento
 * raiz va con prefijo ({@code <ec:validarComprobante>}) pero sus hijos no
 * ({@code <xml>}). Declararlo {@code QUALIFIED} produciria un sobre con los
 * hijos en el namespace del SRI, que el servidor no reconoceria y devolveria
 * como campos ausentes -un fallo silencioso, no un error de transporte-. De ahi
 * que sea lo primero que se fija aqui, a nivel de paquete.
 *
 * <h2>...salvo los elementos GLOBALES, que si van cualificados</h2>
 *
 * <p>{@code elementFormDefault} gobierna solo a los elementos LOCALES. Los que
 * el esquema declara globales y luego referencia con {@code ref="tns:..."}
 * -aqui {@code comprobante} y {@code mensaje}- llevan siempre el namespace del
 * esquema. La respuesta real del SRI mezcla por tanto las dos formas, y cada
 * uno de esos campos lo documenta en su propia clase.
 *
 * <p>Es la trampa de este contrato, y su modo de fallo es silencioso: si se
 * declaran sin namespace, JAXB no los encuentra, devuelve listas vacias y no
 * lanza nada. Una DEVUELTA se guardaria sin los mensajes que la explican, y una
 * respuesta AUTORIZADA se leeria como "aun sin resolver". Por eso el WSDL
 * oficial esta versionado en {@code src/test/resources/sri/wsdl/} y
 * {@code SriBindingContraWsdlTest} contrasta el binding contra el, en lugar de
 * confiar en respuestas de ejemplo escritas a mano.
 *
 * <p>Cada servicio vive en su propio paquete porque en JAXB el namespace es una
 * propiedad DEL PAQUETE. Las clases de mensaje ({@code MensajeWs}) se repiten
 * en recepcion y autorizacion por ese motivo, no por descuido: son tipos
 * distintos en namespaces distintos.
 */
@jakarta.xml.bind.annotation.XmlSchema(
        namespace = com.biopet.facturacion.sri.ws.recepcion.NamespaceRecepcion.URI,
        elementFormDefault = jakarta.xml.bind.annotation.XmlNsForm.UNQUALIFIED)
package com.biopet.facturacion.sri.ws.recepcion;
