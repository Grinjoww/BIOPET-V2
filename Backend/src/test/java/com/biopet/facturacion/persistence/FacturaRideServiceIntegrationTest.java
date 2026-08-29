package com.biopet.facturacion.persistence;

import com.biopet.entity.Usuario;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.entity.TipoIdentificacionSri;
import com.biopet.facturacion.exception.RideNoDisponibleException;
import com.biopet.facturacion.firma.CertificadoPruebaFactory;
import com.biopet.facturacion.service.FacturaEmisionService;
import com.biopet.facturacion.service.FacturaFirmaService;
import com.biopet.facturacion.service.FacturaRideService;
import com.biopet.facturacion.service.FacturaSriService;
import com.biopet.facturacion.service.FacturaXmlService;
import com.biopet.facturacion.service.command.CrearFacturaBorradorCommand;
import com.biopet.facturacion.service.command.DetalleBorradorCommand;
import com.biopet.facturacion.service.command.EmitirFacturaCommand;
import com.biopet.facturacion.service.command.PagoBorradorCommand;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.anything;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

/**
 * Flujo completo contra PostgreSQL real y el SRI simulado (sin red): borrador
 * -&gt; emitir -&gt; XML -&gt; firmar -&gt; enviar (recepcion RECIBIDA, autorizacion
 * AUT) -&gt; RIDE.
 *
 * <p>Mismo patron que {@code XmlAutorizadoIntegrationTest}: no es
 * {@code @Transactional} porque cada paso debe confirmar antes del siguiente.
 */
@SpringBootTest
class FacturaRideServiceIntegrationTest extends FacturaEscenarioTestBase {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 25);
    private static final Path P12 = Path.of("target", "tmp", "ride-service.p12");

    @DynamicPropertySource
    static void configuracion(DynamicPropertyRegistry registry) throws Exception {
        CertificadoPruebaFactory.valido(P12);
        registry.add("sri.firma.certificado.path", P12::toString);
        registry.add("sri.firma.certificado.password", () -> CertificadoPruebaFactory.PASSWORD);
        registry.add("sri.soap.recepcion-url", () -> "http://localhost:1/RecepcionComprobantesOffline");
        registry.add("sri.soap.autorizacion-url", () -> "http://localhost:1/AutorizacionComprobantesOffline");
    }

    private static Long conceptoCompartidoId;

    @Autowired FacturaEmisionService emisionService;
    @Autowired FacturaXmlService xmlService;
    @Autowired FacturaFirmaService firmaService;
    @Autowired FacturaSriService sriService;
    @Autowired FacturaRideService rideService;

    @Autowired @Qualifier("sriRecepcionWebServiceTemplate")
    WebServiceTemplate plantillaRecepcion;

    @Autowired @Qualifier("sriAutorizacionWebServiceTemplate")
    WebServiceTemplate plantillaAutorizacion;

    private MockWebServiceServer sriRecepcion;
    private MockWebServiceServer sriAutorizacion;

    @BeforeEach
    void prepararSriSimulado() {
        sriRecepcion = MockWebServiceServer.createServer(plantillaRecepcion);
        sriAutorizacion = MockWebServiceServer.createServer(plantillaAutorizacion);

        if (conceptoCompartidoId == null) {
            String codigoPorcentaje = nuevoCodigoPorcentaje();
            nuevaTarifa(codigoPorcentaje, "15.00", LocalDate.of(2020, 1, 1), null);
            conceptoCompartidoId = nuevoConcepto(codigoPorcentaje, "20.000000").getId();
        }
    }

    // ==================================================================
    // AUTORIZADA: genera el RIDE
    // ==================================================================

    @Test
    void unaFacturaAutorizadaGeneraUnRidePdfValidoConLosDatosFiscalesReales() throws Exception {
        Escenario escenario = new Escenario();
        Factura autorizada = escenario.autorizada();

        FacturaDocumento documento = rideService.generarRide(autorizada.getId());

        assertThat(documento.getId()).isNotNull();
        assertThat(documento.getTipo()).isEqualTo(TipoDocumentoFactura.RIDE_PDF);
        assertThat(documento.getBytes()).isEqualTo(documento.getContenido().length);
        assertThat(documento.getSha256())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(FacturaXmlService.sha256(documento.getContenido()));
        assertThat(new String(documento.getContenido(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        String texto = textoCompleto(documento.getContenido());
        assertThat(texto).contains(autorizada.getEstablecimiento() + "-" + autorizada.getPuntoEmisionCodigo());
        assertThat(texto).contains(escenario.emisor.getRuc());
        assertThat(texto).contains(autorizada.getNumeroAutorizacion());
        assertThat(texto).contains(autorizada.getClaveAcceso());
        assertThat(texto).contains("PERSONA FICTICIA");
        assertThat(texto).contains(escenario.concepto.getDescripcion());
        assertThat(texto).contains("46.00");

        // Se archivo junto a los otros documentos del pipeline (XML_GENERADO, XML_FIRMADO, XML_AUTORIZADO).
        assertThat(facturaDocumentoRepository.findAllByFactura_Id(autorizada.getId())).hasSize(4);
    }

    // ==================================================================
    // BORRADOR / EMITIDA / RECHAZADA: nunca generan RIDE
    // ==================================================================

    @Test
    void unBorradorNoGeneraRide() {
        Escenario escenario = new Escenario();
        Factura borrador = escenario.borrador();

        assertThatThrownBy(() -> rideService.generarRide(borrador.getId()))
                .isInstanceOf(RideNoDisponibleException.class)
                .hasMessageContaining("BORRADOR");
        assertThat(facturaDocumentoRepository.findByFactura_IdAndTipo(borrador.getId(), TipoDocumentoFactura.RIDE_PDF))
                .isEmpty();
    }

    @Test
    void unaFacturaEmitidaTodaviaSinAutorizarNoGeneraRide() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();

        assertThatThrownBy(() -> rideService.generarRide(emitida.getId()))
                .isInstanceOf(RideNoDisponibleException.class)
                .hasMessageContaining("EMITIDA");
        assertThat(facturaDocumentoRepository.findByFactura_IdAndTipo(emitida.getId(), TipoDocumentoFactura.RIDE_PDF))
                .isEmpty();
    }

    @Test
    void unaFacturaRechazadaPorElSriNoGeneraRide() {
        Escenario escenario = new Escenario();
        Factura emitida = escenario.emitir();
        xmlService.generarXml(emitida.getId());
        firmaService.firmarFactura(emitida.getId());

        sriRecepcion.expect(anything()).andRespond(withPayload(devuelta()));
        sriService.enviar(emitida.getId());

        Factura rechazada = facturaRepository.findById(emitida.getId()).orElseThrow();
        assertThat(rechazada.getEstado()).isEqualTo(com.biopet.facturacion.entity.EstadoFactura.RECHAZADA);

        assertThatThrownBy(() -> rideService.generarRide(rechazada.getId()))
                .isInstanceOf(RideNoDisponibleException.class)
                .hasMessageContaining("RECHAZADA");
    }

    @Test
    void argumentosInvalidosSeRechazan() {
        assertThatThrownBy(() -> rideService.generarRide(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rideService.generarRide(987_654_321L))
                .isInstanceOf(com.biopet.exception.RecursoNoEncontradoException.class);
    }

    // ==================================================================
    // Idempotencia
    // ==================================================================

    @Test
    void generarDosVecesDevuelveElMismoDocumentoYUnaSolaFila() {
        Escenario escenario = new Escenario();
        Factura autorizada = escenario.autorizada();

        FacturaDocumento primero = rideService.generarRide(autorizada.getId());
        FacturaDocumento segundo = rideService.generarRide(autorizada.getId());

        assertThat(segundo.getId()).isEqualTo(primero.getId());
        assertThat(segundo.getSha256()).isEqualTo(primero.getSha256());
        assertThat(segundo.getContenido()).isEqualTo(primero.getContenido());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM factura_documentos WHERE factura_id = ? AND tipo = 'RIDE_PDF'",
                Integer.class, autorizada.getId())).isEqualTo(1);
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    private static String textoCompleto(byte[] pdf) throws Exception {
        PdfReader lector = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            StringBuilder acumulado = new StringBuilder();
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                acumulado.append(extractor.getTextFromPage(pagina)).append('\n');
            }
            return acumulado.toString();
        } finally {
            lector.close();
        }
    }

    private static StringSource recibida() {
        return new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante>
                    <estado>RECIBIDA</estado><comprobantes/>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """);
    }

    private static StringSource devuelta() {
        return new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante>
                    <estado>DEVUELTA</estado>
                    <comprobantes>
                      <ns:comprobante>
                        <mensajes><ns:mensaje>
                          <identificador>35</identificador>
                          <mensaje>ARCHIVO NO CUMPLE ESTRUCTURA XML</mensaje>
                          <tipo>ERROR</tipo>
                        </ns:mensaje></mensajes>
                      </ns:comprobante>
                    </comprobantes>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """);
    }

    private static StringSource respuestaAutorizada(String clave) {
        return new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <claveAccesoConsultada>%s</claveAccesoConsultada>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones><ns:autorizacion>
                      <estado>AUTORIZADO</estado>
                      <numeroAutorizacion>%s</numeroAutorizacion>
                      <fechaAutorizacion>2026-09-25T10:30:00-05:00</fechaAutorizacion>
                      <ambiente>PRUEBAS</ambiente>
                      <comprobante>&lt;factura id="comprobante" version="2.1.0"&gt;&lt;claveAcceso&gt;%s&lt;/claveAcceso&gt;&lt;/factura&gt;</comprobante>
                      <mensajes/>
                    </ns:autorizacion></autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(clave, clave, clave));
    }

    // ==================================================================
    // Escenario
    // ==================================================================

    private final class Escenario {
        private final EmisorFiscal emisor = nuevoEmisor();
        private final PuntoEmision punto = nuevoPunto(emisor);
        private final ConceptoFacturable concepto;
        private final Usuario usuario = nuevoUsuario();
        private final DatosFacturacion datos;

        Escenario() {
            nuevoContador(punto, AmbienteSri.PRUEBAS, 0L);
            concepto = conceptoFacturableRepository.findById(conceptoCompartidoId).orElseThrow();
            datos = nuevosDatos(usuario, TipoIdentificacionSri.CEDULA, "0000000000", "PERSONA FICTICIA");
        }

        Factura borrador() {
            Factura borrador = borradorService.crear(
                    new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));
            borradorService.seleccionarComprador(borrador.getId(), datos.getId());
            borradorService.reemplazarDetalles(borrador.getId(),
                    List.of(DetalleBorradorCommand.de(concepto.getId(), new BigDecimal("2"))));
            borradorService.reemplazarPagos(borrador.getId(), List.of(
                    PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("46.00"))));
            return facturaRepository.findById(borrador.getId()).orElseThrow();
        }

        Factura emitir() {
            Factura borrador = borrador();
            return emisionService.emitir(new EmitirFacturaCommand(borrador.getId(), punto.getId(), AmbienteSri.PRUEBAS));
        }

        Factura autorizada() {
            Factura emitida = emitir();
            xmlService.generarXml(emitida.getId());
            firmaService.firmarFactura(emitida.getId());
            String clave = emitida.getClaveAcceso();

            sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
            sriAutorizacion.expect(anything()).andRespond(withPayload(respuestaAutorizada(clave)));
            sriService.enviar(emitida.getId());

            return facturaRepository.findById(emitida.getId()).orElseThrow();
        }
    }
}
