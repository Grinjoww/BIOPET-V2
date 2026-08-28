package com.biopet.facturacion.sri;

import com.biopet.facturacion.entity.EstadoAutorizacionSri;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.connectionTo;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withException;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;
import static org.springframework.ws.test.client.ResponseCreators.withServerOrReceiverFault;

/**
 * Dialogo con {@code AutorizacionComprobantesOffline}, contra un servidor SOAP
 * simulado. Sin red, como el resto de la suite.
 */
class SriAutorizacionClientTest {

    private static final String CLAVE = "2609202601099000000010012001000000001123456781";

    private MockWebServiceServer servidor;
    private SriAutorizacionClient cliente;

    @BeforeEach
    void setUp() {
        WebServiceTemplate plantilla = SriSoapTestFixture.plantillaAutorizacion();
        servidor = MockWebServiceServer.createServer(plantilla);
        cliente = new SriAutorizacionClient(plantilla);
    }

    // ==================================================================
    // Peticion
    // ==================================================================

    @Test
    void consultaPorLaClaveDeAccesoRecibida() {
        servidor.expect(connectionTo(SriSoapTestFixture.URI_AUTORIZACION))
                .andExpect(payload(peticion()))
                .andRespond(withPayload(autorizada()));

        cliente.autorizacionComprobante(CLAVE);

        servidor.verify();
    }

    @Test
    void sinClaveNoSeConsultaNada() {
        assertThatThrownBy(() -> cliente.autorizacionComprobante(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cliente.autorizacionComprobante("   "))
                .isInstanceOf(IllegalArgumentException.class);
        servidor.verify();
    }

    // ==================================================================
    // AUT
    // ==================================================================

    @Test
    void autorizadoDevuelveNumeroFechaYComprobante() {
        servidor.expect(payload(peticion())).andRespond(withPayload(autorizada()));

        RespuestaAutorizacionSri respuesta = cliente.autorizacionComprobante(CLAVE);

        assertThat(respuesta.estado()).isEqualTo(EstadoAutorizacionSri.AUT);
        assertThat(respuesta.autorizada()).isTrue();
        assertThat(respuesta.numeroAutorizacion()).isEqualTo(CLAVE);
        assertThat(respuesta.fechaAutorizacion())
                .isEqualTo(Instant.parse("2026-09-15T15:30:00Z"));
        assertThat(respuesta.ambiente()).isEqualTo("PRUEBAS");
        assertThat(respuesta.comprobante())
                .contains("<factura id=\"comprobante\"")
                .contains("<claveAcceso>" + CLAVE + "</claveAcceso>");
        servidor.verify();
    }

    @Test
    void aceptaTambienElCodigoAbreviadoDeLaFichaTecnica() {
        servidor.expect(payload(peticion()))
                .andRespond(withPayload(conEstado("AUT", "<comprobante>&lt;factura/&gt;</comprobante>")));

        assertThat(cliente.autorizacionComprobante(CLAVE).estado())
                .isEqualTo(EstadoAutorizacionSri.AUT);
        servidor.verify();
    }

    @Test
    void entreVariasAutorizacionesSeQuedaConLaAutorizada() {
        servidor.expect(payload(peticion())).andRespond(withPayload(new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <claveAccesoConsultada>%s</claveAccesoConsultada>
                    <numeroComprobantes>2</numeroComprobantes>
                    <autorizaciones>
                      <ns:autorizacion>
                        <estado>NO AUTORIZADO</estado>
                        <mensajes><ns:mensaje><identificador>70</identificador>
                          <mensaje>INTENTO PREVIO</mensaje></ns:mensaje></mensajes>
                      </ns:autorizacion>
                      <ns:autorizacion>
                        <estado>AUTORIZADO</estado>
                        <numeroAutorizacion>%s</numeroAutorizacion>
                        <fechaAutorizacion>2026-09-15T10:30:00-05:00</fechaAutorizacion>
                        <comprobante>&lt;factura id="comprobante"/&gt;</comprobante>
                      </ns:autorizacion>
                    </autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(CLAVE, CLAVE))));

        RespuestaAutorizacionSri respuesta = cliente.autorizacionComprobante(CLAVE);

        // Quedarse con el primer elemento habria marcado RECHAZADA una factura
        // que el SRI acaba de autorizar en la MISMA respuesta.
        assertThat(respuesta.estado()).isEqualTo(EstadoAutorizacionSri.AUT);
        assertThat(respuesta.numeroAutorizacion()).isEqualTo(CLAVE);
        // Y no se pierde el mensaje del intento anterior.
        assertThat(respuesta.mensajes()).extracting(MensajeSri::identificador).contains("70");
        servidor.verify();
    }

    // ==================================================================
    // NAT
    // ==================================================================

    @Test
    void noAutorizadoConservaTodosLosMensajes() {
        servidor.expect(payload(peticion())).andRespond(withPayload(new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <claveAccesoConsultada>%s</claveAccesoConsultada>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones>
                      <ns:autorizacion>
                        <estado>NO AUTORIZADO</estado>
                        <fechaAutorizacion>2026-09-15T10:30:00-05:00</fechaAutorizacion>
                        <ambiente>PRUEBAS</ambiente>
                        <mensajes>
                          <ns:mensaje>
                            <identificador>39</identificador>
                            <mensaje>FIRMA INVALIDA</mensaje>
                            <informacionAdicional>Certificado no confiable</informacionAdicional>
                            <tipo>ERROR</tipo>
                          </ns:mensaje>
                          <ns:mensaje>
                            <identificador>65</identificador>
                            <mensaje>NO EXISTE NUMERO DE AUTORIZACION</mensaje>
                            <tipo>ERROR</tipo>
                          </ns:mensaje>
                        </mensajes>
                      </ns:autorizacion>
                    </autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(CLAVE))));

        RespuestaAutorizacionSri respuesta = cliente.autorizacionComprobante(CLAVE);

        assertThat(respuesta.estado()).isEqualTo(EstadoAutorizacionSri.NAT);
        assertThat(respuesta.autorizada()).isFalse();
        assertThat(respuesta.mensajes()).hasSize(2);
        assertThat(respuesta.mensajes().get(0).informacionAdicional())
                .isEqualTo("Certificado no confiable");
        assertThat(respuesta.comprobante()).isNull();
        servidor.verify();
    }

    // ==================================================================
    // PPR
    // ==================================================================

    @Test
    void enProcesamientoEsPpr() {
        servidor.expect(payload(peticion()))
                .andRespond(withPayload(conEstado("EN PROCESAMIENTO", "")));

        RespuestaAutorizacionSri respuesta = cliente.autorizacionComprobante(CLAVE);

        assertThat(respuesta.estado()).isEqualTo(EstadoAutorizacionSri.PPR);
        assertThat(respuesta.pendiente()).isTrue();
        servidor.verify();
    }

    @Test
    void sinAutorizacionesSeNormalizaAPprYNoAError() {
        servidor.expect(payload(peticion())).andRespond(withPayload(new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <claveAccesoConsultada>%s</claveAccesoConsultada>
                    <numeroComprobantes>0</numeroComprobantes>
                    <autorizaciones/>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(CLAVE))));

        RespuestaAutorizacionSri respuesta = cliente.autorizacionComprobante(CLAVE);

        // Es el caso normal justo despues de enviar: el SRI aun no ha resuelto.
        assertThat(respuesta.estado()).isEqualTo(EstadoAutorizacionSri.PPR);
        assertThat(respuesta.numeroAutorizacion()).isNull();
        assertThat(respuesta.mensajes()).isEmpty();
        servidor.verify();
    }

    // ==================================================================
    // Fechas y formatos defensivos
    // ==================================================================

    @Test
    void unaFechaSinOffsetSeInterpretaEnLaZonaDelSri() {
        servidor.expect(payload(peticion())).andRespond(withPayload(new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones><ns:autorizacion>
                      <estado>AUTORIZADO</estado>
                      <numeroAutorizacion>%s</numeroAutorizacion>
                      <fechaAutorizacion>2026-09-15T10:30:00</fechaAutorizacion>
                    </ns:autorizacion></autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(CLAVE))));

        // America/Guayaquil es UTC-5 todo el ano.
        assertThat(cliente.autorizacionComprobante(CLAVE).fechaAutorizacion())
                .isEqualTo(Instant.parse("2026-09-15T15:30:00Z"));
        servidor.verify();
    }

    @Test
    void unaFechaIlegibleNoTumbaUnAutorizado() {
        servidor.expect(payload(peticion())).andRespond(withPayload(new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones><ns:autorizacion>
                      <estado>AUTORIZADO</estado>
                      <numeroAutorizacion>%s</numeroAutorizacion>
                      <fechaAutorizacion>15/09/2026 10:30</fechaAutorizacion>
                    </ns:autorizacion></autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(CLAVE))));

        RespuestaAutorizacionSri respuesta = cliente.autorizacionComprobante(CLAVE);

        // Perder la marca temporal es molesto; perder el AUT seria grave.
        assertThat(respuesta.estado()).isEqualTo(EstadoAutorizacionSri.AUT);
        assertThat(respuesta.numeroAutorizacion()).isEqualTo(CLAVE);
        assertThat(respuesta.fechaAutorizacion()).isNull();
        servidor.verify();
    }

    // ==================================================================
    // Fallos tecnicos
    // ==================================================================

    @Test
    void unTimeoutDeAutorizacionSeClasificaComoTimeout() {
        servidor.expect(payload(peticion()))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> cliente.autorizacionComprobante(CLAVE))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.TIMEOUT);
    }

    @Test
    void unSoapFaultEnAutorizacionSeClasificaComoSoapFault() {
        servidor.expect(payload(peticion()))
                .andRespond(withServerOrReceiverFault("Fallo interno", Locale.getDefault()));

        assertThatThrownBy(() -> cliente.autorizacionComprobante(CLAVE))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.SOAP_FAULT);
    }

    @Test
    void unEstadoDesconocidoNoSeAdivina() {
        servidor.expect(payload(peticion()))
                .andRespond(withPayload(conEstado("PENDIENTE DE REVISION MANUAL", "")));

        assertThatThrownBy(() -> cliente.autorizacionComprobante(CLAVE))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.RESPUESTA_INVALIDA);
    }

    @Test
    void unaRespuestaSinCuerpoEsFalloTecnico() {
        servidor.expect(payload(peticion())).andRespond(withPayload(new StringSource(
                "<ns:autorizacionComprobanteResponse "
                        + "xmlns:ns=\"http://ec.gob.sri.ws.autorizacion\"/>")));

        assertThatThrownBy(() -> cliente.autorizacionComprobante(CLAVE))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.RESPUESTA_INVALIDA);
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    private static StringSource peticion() {
        return new StringSource(
                "<ns:autorizacionComprobante xmlns:ns=\"http://ec.gob.sri.ws.autorizacion\">"
                        + "<claveAccesoComprobante>" + CLAVE + "</claveAccesoComprobante>"
                        + "</ns:autorizacionComprobante>");
    }

    private static StringSource autorizada() {
        return new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <claveAccesoConsultada>%s</claveAccesoConsultada>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones>
                      <ns:autorizacion>
                        <estado>AUTORIZADO</estado>
                        <numeroAutorizacion>%s</numeroAutorizacion>
                        <fechaAutorizacion>2026-09-15T10:30:00-05:00</fechaAutorizacion>
                        <ambiente>PRUEBAS</ambiente>
                        <comprobante>&lt;factura id="comprobante" version="2.1.0"&gt;&lt;claveAcceso&gt;%s&lt;/claveAcceso&gt;&lt;/factura&gt;</comprobante>
                        <mensajes/>
                      </ns:autorizacion>
                    </autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(CLAVE, CLAVE, CLAVE));
    }

    private static StringSource conEstado(String estado, String extra) {
        return new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <claveAccesoConsultada>%s</claveAccesoConsultada>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones><ns:autorizacion>
                      <estado>%s</estado>
                      %s
                    </ns:autorizacion></autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(CLAVE, estado, extra));
    }
}
