package com.biopet.facturacion.sri;

import com.biopet.facturacion.entity.EstadoRecepcionSri;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.connectionTo;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withException;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;
import static org.springframework.ws.test.client.ResponseCreators.withServerOrReceiverFault;

/**
 * Dialogo completo con {@code RecepcionComprobantesOffline}, contra un servidor
 * SOAP simulado.
 *
 * <p>Ni un solo test de esta clase sale a Internet: {@link MockWebServiceServer}
 * sustituye el transporte de la plantilla. Es un requisito, no una comodidad -la
 * suite tiene que poder correr en un CI sin salida a la red-, pero ademas
 * permite provocar a voluntad cosas que el SRI real no produce cuando conviene:
 * un SOAP Fault, un timeout o un cuerpo malformado.
 */
class SriRecepcionClientTest {

    private static final byte[] XML_FIRMADO =
            "<factura id=\"comprobante\"/>".getBytes(StandardCharsets.UTF_8);

    private WebServiceTemplate plantilla;
    private MockWebServiceServer servidor;
    private SriRecepcionClient cliente;

    @BeforeEach
    void setUp() {
        plantilla = SriSoapTestFixture.plantillaRecepcion();
        servidor = MockWebServiceServer.createServer(plantilla);
        cliente = new SriRecepcionClient(plantilla);
    }

    // ==================================================================
    // Peticion
    // ==================================================================

    @Test
    void enviaElXmlFirmadoEnBase64AlEndpointConfigurado() {
        String base64 = Base64.getEncoder().encodeToString(XML_FIRMADO);
        servidor.expect(connectionTo(SriSoapTestFixture.URI_RECEPCION))
                .andExpect(payload(new StringSource(
                        "<ns:validarComprobante xmlns:ns=\"http://ec.gob.sri.ws.recepcion\">"
                                + "<xml>" + base64 + "</xml></ns:validarComprobante>")))
                .andRespond(withPayload(recibida()));

        cliente.validarComprobante(XML_FIRMADO);

        // El matcher de payload compara el XML de la peticion completo: si el
        // namespace, el nombre del elemento o el base64 no fueran exactos, esto
        // ya habria fallado. Es la comprobacion de que se envian los bytes
        // persistidos y no una reserializacion.
        servidor.verify();
    }

    @Test
    void unComprobanteVacioNiSiquieraSeEnvia() {
        assertThatThrownBy(() -> cliente.validarComprobante(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cliente.validarComprobante(null))
                .isInstanceOf(IllegalArgumentException.class);
        servidor.verify();
    }

    // ==================================================================
    // RECIBIDA
    // ==================================================================

    @Test
    void recibidaSeInterpretaComoRecibidaYSinMensajes() {
        servidor.expect(payload(peticion())).andRespond(withPayload(recibida()));

        RespuestaRecepcionSri respuesta = cliente.validarComprobante(XML_FIRMADO);

        assertThat(respuesta.estado()).isEqualTo(EstadoRecepcionSri.RECIBIDA);
        assertThat(respuesta.recibida()).isTrue();
        assertThat(respuesta.mensajes()).isEmpty();
        assertThat(respuesta.duracionMs()).isNotNegative();
        servidor.verify();
    }

    // ==================================================================
    // DEVUELTA
    // ==================================================================

    @Test
    void devueltaConservaTodosLosMensajesDeTodosLosComprobantes() {
        servidor.expect(payload(peticion())).andRespond(withPayload(new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante>
                    <estado>DEVUELTA</estado>
                    <comprobantes>
                      <ns:comprobante>
                        <claveAcceso>2609202601099000000010012001000000001123456781</claveAcceso>
                        <mensajes>
                          <ns:mensaje>
                            <identificador>39</identificador>
                            <mensaje>FIRMA INVALIDA</mensaje>
                            <informacionAdicional>El certificado no es de confianza</informacionAdicional>
                            <tipo>ERROR</tipo>
                          </ns:mensaje>
                          <ns:mensaje>
                            <identificador>52</identificador>
                            <mensaje>ERROR EN LA ESTRUCTURA DE LA TABLA</mensaje>
                            <tipo>ADVERTENCIA</tipo>
                          </ns:mensaje>
                        </mensajes>
                      </ns:comprobante>
                      <ns:comprobante>
                        <claveAcceso>2609202601099000000010012001000000002123456782</claveAcceso>
                        <mensajes>
                          <ns:mensaje>
                            <identificador>45</identificador>
                            <mensaje>ERROR EN DIGITO VERIFICADOR</mensaje>
                          </ns:mensaje>
                        </mensajes>
                      </ns:comprobante>
                    </comprobantes>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """)));

        RespuestaRecepcionSri respuesta = cliente.validarComprobante(XML_FIRMADO);

        assertThat(respuesta.estado()).isEqualTo(EstadoRecepcionSri.DEVUELTA);
        assertThat(respuesta.recibida()).isFalse();

        // Tres mensajes, no uno: los secundarios y los del segundo comprobante
        // no se pierden.
        assertThat(respuesta.mensajes()).hasSize(3);
        assertThat(respuesta.mensajes()).extracting(MensajeSri::identificador)
                .containsExactly("39", "52", "45");

        MensajeSri primero = respuesta.mensajes().get(0);
        assertThat(primero.mensaje()).isEqualTo("FIRMA INVALIDA");
        assertThat(primero.informacionAdicional()).isEqualTo("El certificado no es de confianza");
        assertThat(primero.tipo()).isEqualTo("ERROR");

        // Los campos opcionales ausentes llegan como null, no como cadena vacia.
        assertThat(respuesta.mensajes().get(2).informacionAdicional()).isNull();
        assertThat(respuesta.mensajes().get(2).tipo()).isNull();

        assertThat(respuesta.claveAcceso())
                .isEqualTo("2609202601099000000010012001000000001123456781");
        servidor.verify();
    }

    @Test
    void reconoceElCodigoDeClaveYaRegistrada() {
        servidor.expect(payload(peticion())).andRespond(withPayload(devueltaConCodigo("43")));

        RespuestaRecepcionSri respuesta = cliente.validarComprobante(XML_FIRMADO);

        assertThat(respuesta.contieneCodigo(CodigosMensajeSri.CLAVE_REGISTRADA)).isTrue();
        assertThat(respuesta.contieneCodigo(CodigosMensajeSri.EN_PROCESAMIENTO)).isFalse();
        servidor.verify();
    }

    @Test
    void reconoceElCodigoDeEnProcesamiento() {
        servidor.expect(payload(peticion())).andRespond(withPayload(devueltaConCodigo("70")));

        RespuestaRecepcionSri respuesta = cliente.validarComprobante(XML_FIRMADO);

        assertThat(respuesta.contieneCodigo(CodigosMensajeSri.EN_PROCESAMIENTO)).isTrue();
        servidor.verify();
    }

    // ==================================================================
    // Fallos tecnicos: cada uno con su tipo
    // ==================================================================

    @Test
    void unTimeoutDeLecturaSeClasificaComoTimeoutYNoComoRechazo() {
        servidor.expect(payload(peticion()))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> cliente.validarComprobante(XML_FIRMADO))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.TIMEOUT);
    }

    @Test
    void unFalloDeConexionSeClasificaComoConexion() {
        servidor.expect(payload(peticion()))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> cliente.validarComprobante(XML_FIRMADO))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.CONEXION);
    }

    @Test
    void otroErrorDeEntradaSalidaTambienEsConexion() {
        servidor.expect(payload(peticion()))
                .andRespond(withException(new IOException("stream cerrado")));

        assertThatThrownBy(() -> cliente.validarComprobante(XML_FIRMADO))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.CONEXION);
    }

    @Test
    void unSoapFaultSeClasificaComoSoapFault() {
        servidor.expect(payload(peticion()))
                .andRespond(withServerOrReceiverFault("Servicio no disponible", Locale.getDefault()));

        assertThatThrownBy(() -> cliente.validarComprobante(XML_FIRMADO))
                .isInstanceOf(SriComunicacionException.class)
                .hasMessageContaining("SOAP Fault")
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.SOAP_FAULT);
    }

    @Test
    void unEstadoFueraDelContratoNoSeAdivina() {
        servidor.expect(payload(peticion())).andRespond(withPayload(new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante><estado>PERPLEJA</estado></RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """)));

        // No se degrada a DEVUELTA ni a RECIBIDA: se declara fallo tecnico, que
        // deja la factura intacta y reintentable.
        assertThatThrownBy(() -> cliente.validarComprobante(XML_FIRMADO))
                .isInstanceOf(SriComunicacionException.class)
                .hasMessageContaining("PERPLEJA")
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.RESPUESTA_INVALIDA);
    }

    @Test
    void unaRespuestaSinCuerpoEsFalloTecnico() {
        servidor.expect(payload(peticion())).andRespond(withPayload(new StringSource(
                "<ns:validarComprobanteResponse xmlns:ns=\"http://ec.gob.sri.ws.recepcion\"/>")));

        assertThatThrownBy(() -> cliente.validarComprobante(XML_FIRMADO))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.RESPUESTA_INVALIDA);
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    private static StringSource peticion() {
        return new StringSource(
                "<ns:validarComprobante xmlns:ns=\"http://ec.gob.sri.ws.recepcion\"><xml>"
                        + Base64.getEncoder().encodeToString(XML_FIRMADO)
                        + "</xml></ns:validarComprobante>");
    }

    private static StringSource recibida() {
        return new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante>
                    <estado>RECIBIDA</estado>
                    <comprobantes/>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """);
    }

    private static StringSource devueltaConCodigo(String codigo) {
        return new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante>
                    <estado>DEVUELTA</estado>
                    <comprobantes>
                      <ns:comprobante>
                        <mensajes>
                          <ns:mensaje>
                            <identificador>%s</identificador>
                            <mensaje>MENSAJE DEL CATALOGO</mensaje>
                            <tipo>ERROR</tipo>
                          </ns:mensaje>
                        </mensajes>
                      </ns:comprobante>
                    </comprobantes>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """.formatted(codigo));
    }
}
