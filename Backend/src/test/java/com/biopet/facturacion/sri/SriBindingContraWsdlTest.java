package com.biopet.facturacion.sri;

import com.biopet.facturacion.entity.EstadoAutorizacionSri;
import com.biopet.facturacion.entity.EstadoRecepcionSri;
import org.junit.jupiter.api.Test;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.anything;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

/**
 * Guardia del binding: las respuestas simuladas de la suite tienen la forma que
 * exige el CONTRATO OFICIAL, y los bindings las leen sin perder nada.
 *
 * <h2>Por que existe este test</h2>
 *
 * <p>Por un fallo real cometido al escribir esta fase, que ningun otro test
 * habria detectado nunca. El esquema del SRI declara
 * {@code elementFormDefault="unqualified"}, de donde se dedujo -mal- que todos
 * los hijos van sin namespace. Pero {@code comprobante}, {@code mensaje} y
 * {@code autorizacion} se declaran con {@code ref="tns:..."}: son referencias a
 * elementos GLOBALES, y esos llevan SIEMPRE el namespace del esquema. La
 * respuesta real mezcla las dos formas.
 *
 * <p>El problema no es el error, es el modo de fallo. Con esos elementos
 * declarados sin namespace, JAXB no los encuentra y devuelve listas vacias, sin
 * lanzar nada:
 *
 * <ul>
 *   <li>una DEVUELTA se persistiria sin ninguno de los mensajes que explican
 *       por que se devolvio;</li>
 *   <li>una respuesta AUTORIZADA se leeria como "sin autorizaciones", que el
 *       contrato define como pendiente, de modo que NINGUNA factura llegaria
 *       jamas a AUTORIZADA.</li>
 * </ul>
 *
 * <p>Escribir las respuestas de prueba a mano no protege de esto: si el mock y
 * el binding comparten la misma suposicion equivocada, todos los tests pasan en
 * verde. La unica defensa es contrastar contra el contrato publicado, que es lo
 * que se hace aqui con el WSDL versionado en
 * {@code src/test/resources/sri/wsdl/} (copia literal, descargada de CELCER).
 *
 * <p>Sin red: el esquema se extrae del WSDL local.
 */
class SriBindingContraWsdlTest {

    private static final String NS_REC = "http://ec.gob.sri.ws.recepcion";
    private static final String NS_AUT = "http://ec.gob.sri.ws.autorizacion";

    private static final String CLAVE = "2609202601099000000010012001000000001123456781";

    // ==================================================================
    // Recepcion
    // ==================================================================

    @Test
    void laRespuestaDeRecepcionUsadaEnLaSuiteCumpleElEsquemaOficial() {
        assertThatCode(() -> validar("RecepcionComprobantesOffline.wsdl", devuelta()))
                .doesNotThrowAnyException();
    }

    @Test
    void elBindingDeRecepcionLeeLosMensajesDeUnaRespuestaConformeAlContrato() {
        WebServiceTemplate plantilla = SriSoapTestFixture.plantillaRecepcion();
        MockWebServiceServer servidor = MockWebServiceServer.createServer(plantilla);
        servidor.expect(anything()).andRespond(withPayload(new StringSource(devuelta())));

        RespuestaRecepcionSri respuesta = new SriRecepcionClient(plantilla)
                .validarComprobante("<factura/>".getBytes(StandardCharsets.UTF_8));

        assertThat(respuesta.estado()).isEqualTo(EstadoRecepcionSri.DEVUELTA);
        // Si <comprobante> o <mensaje> se declarasen sin namespace, esto seria
        // una lista vacia y el test moriria aqui, que es justo lo que se busca.
        assertThat(respuesta.mensajes()).hasSize(2);
        assertThat(respuesta.mensajes()).extracting(MensajeSri::identificador)
                .containsExactly("43", "70");
        assertThat(respuesta.claveAcceso()).isEqualTo(CLAVE);
        servidor.verify();
    }

    // ==================================================================
    // Autorizacion
    // ==================================================================

    @Test
    void laRespuestaDeAutorizacionUsadaEnLaSuiteCumpleElEsquemaOficial() {
        assertThatCode(() -> validar("AutorizacionComprobantesOffline.wsdl", autorizada()))
                .doesNotThrowAnyException();
    }

    @Test
    void elBindingDeAutorizacionLeeLaAutorizacionDeUnaRespuestaConformeAlContrato() {
        WebServiceTemplate plantilla = SriSoapTestFixture.plantillaAutorizacion();
        MockWebServiceServer servidor = MockWebServiceServer.createServer(plantilla);
        servidor.expect(anything()).andRespond(withPayload(new StringSource(autorizada())));

        RespuestaAutorizacionSri respuesta =
                new SriAutorizacionClient(plantilla).autorizacionComprobante(CLAVE);

        // Con <autorizacion> mal declarado, esto seria PPR y ninguna factura
        // llegaria nunca a AUTORIZADA.
        assertThat(respuesta.estado()).isEqualTo(EstadoAutorizacionSri.AUT);
        assertThat(respuesta.numeroAutorizacion()).isEqualTo(CLAVE);
        assertThat(respuesta.comprobante()).contains("<factura id=\"comprobante\"");
        assertThat(respuesta.mensajes()).extracting(MensajeSri::identificador)
                .containsExactly("60");
        servidor.verify();
    }

    // ==================================================================
    // El contrato dice lo que creemos que dice
    // ==================================================================

    @Test
    void elEsquemaOficialDeclaraEsosTresElementosComoGlobalesYPorTantoCualificados() {
        // Documenta la regla en la que se apoya todo el binding, leyendola del
        // propio contrato en lugar de fiarse de la memoria.
        String recepcion = leer("RecepcionComprobantesOffline.wsdl");
        assertThat(recepcion).contains("elementFormDefault=\"unqualified\"");
        assertThat(recepcion)
                .as("comprobante y mensaje son elementos globales referenciados con ref=")
                .contains("<xs:element name=\"comprobante\"")
                .contains("<xs:element name=\"mensaje\"")
                .contains("ref=\"tns:comprobante\"")
                .contains("ref=\"tns:mensaje\"");

        String autorizacion = leer("AutorizacionComprobantesOffline.wsdl");
        assertThat(autorizacion).contains("elementFormDefault=\"unqualified\"");
        assertThat(autorizacion)
                .contains("<xs:element name=\"autorizacion\"")
                .contains("ref=\"tns:autorizacion\"");
    }

    @Test
    void unaRespuestaConLosElementosSinCualificarNoCumpleElEsquema() {
        // La forma equivocada -la que se escribio primero- es efectivamente
        // invalida segun el contrato. Esto es lo que convierte al test en una
        // guardia y no en una tautologia.
        String malFormada = """
                <ns:validarComprobanteResponse xmlns:ns="%s">
                  <RespuestaRecepcionComprobante>
                    <estado>DEVUELTA</estado>
                    <comprobantes>
                      <comprobante><claveAcceso>%s</claveAcceso></comprobante>
                    </comprobantes>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """.formatted(NS_REC, CLAVE);

        assertThatThrownBy(() -> validar("RecepcionComprobantesOffline.wsdl", malFormada))
                .isInstanceOf(SAXParseException.class);
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    /**
     * Valida un documento contra el esquema EMBEBIDO en el WSDL oficial.
     *
     * <p>El {@code <xs:schema>} se extrae del {@code <wsdl:types>} y se compila
     * suelto. Funciona porque el esquema del SRI no importa nada externo, asi
     * que no hace falta resolver ninguna referencia ni salir a la red.
     */
    private static void validar(String wsdl, String documento) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        Document contrato;
        try (InputStream in = recurso(wsdl)) {
            contrato = factory.newDocumentBuilder().parse(in);
        }
        NodeList esquemas =
                contrato.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "schema");
        assertThat(esquemas.getLength()).as("esquema embebido en %s", wsdl).isEqualTo(1);

        SchemaFactory schemaFactory =
                SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Schema schema = schemaFactory.newSchema(new DOMSource((Element) esquemas.item(0)));

        Validator validator = schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        validator.validate(new DOMSource(factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(documento.getBytes(StandardCharsets.UTF_8)))));
    }

    private static String leer(String wsdl) {
        try (InputStream in = recurso(wsdl)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("No se pudo leer el WSDL versionado " + wsdl, e);
        }
    }

    private static InputStream recurso(String wsdl) {
        InputStream in = SriBindingContraWsdlTest.class
                .getResourceAsStream("/sri/wsdl/" + wsdl);
        assertThat(in).as("WSDL versionado /sri/wsdl/%s", wsdl).isNotNull();
        return in;
    }

    // ------------------------------------------------------------------
    // Respuestas en la forma EXACTA del contrato: hijos locales sin
    // namespace, elementos globales (comprobante, mensaje, autorizacion)
    // con el.
    // ------------------------------------------------------------------

    private static String devuelta() {
        return """
                <ns:validarComprobanteResponse xmlns:ns="%s">
                  <RespuestaRecepcionComprobante>
                    <estado>DEVUELTA</estado>
                    <comprobantes>
                      <ns:comprobante>
                        <claveAcceso>%s</claveAcceso>
                        <mensajes>
                          <ns:mensaje>
                            <identificador>43</identificador>
                            <mensaje>CLAVE ACCESO REGISTRADA</mensaje>
                            <informacionAdicional>Ya recibido previamente</informacionAdicional>
                            <tipo>ERROR</tipo>
                          </ns:mensaje>
                          <ns:mensaje>
                            <identificador>70</identificador>
                            <mensaje>COMPROBANTE EN PROCESAMIENTO</mensaje>
                          </ns:mensaje>
                        </mensajes>
                      </ns:comprobante>
                    </comprobantes>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """.formatted(NS_REC, CLAVE);
    }

    private static String autorizada() {
        return """
                <ns:autorizacionComprobanteResponse xmlns:ns="%s">
                  <RespuestaAutorizacionComprobante>
                    <claveAccesoConsultada>%s</claveAccesoConsultada>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones>
                      <ns:autorizacion>
                        <estado>AUTORIZADO</estado>
                        <numeroAutorizacion>%s</numeroAutorizacion>
                        <fechaAutorizacion>2026-09-15T10:30:00-05:00</fechaAutorizacion>
                        <ambiente>PRUEBAS</ambiente>
                        <comprobante>&lt;factura id="comprobante" version="2.1.0"/&gt;</comprobante>
                        <mensajes>
                          <ns:mensaje>
                            <identificador>60</identificador>
                            <mensaje>AUTORIZADO CON OBSERVACIONES</mensaje>
                            <tipo>ADVERTENCIA</tipo>
                          </ns:mensaje>
                        </mensajes>
                      </ns:autorizacion>
                    </autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(NS_AUT, CLAVE, CLAVE);
    }
}
