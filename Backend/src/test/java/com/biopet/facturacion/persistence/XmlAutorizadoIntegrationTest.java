package com.biopet.facturacion.persistence;

import com.biopet.entity.Usuario;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.EstadoAutorizacionSri;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.entity.TipoIdentificacionSri;
import com.biopet.facturacion.exception.AutorizacionSriInconsistenteException;
import com.biopet.facturacion.firma.CertificadoPruebaFactory;
import com.biopet.facturacion.service.FacturaEmisionService;
import com.biopet.facturacion.service.FacturaFirmaService;
import com.biopet.facturacion.service.FacturaSriEstadoService;
import com.biopet.facturacion.service.FacturaSriService;
import com.biopet.facturacion.service.FacturaXmlService;
import com.biopet.facturacion.service.command.CrearFacturaBorradorCommand;
import com.biopet.facturacion.service.command.DetalleBorradorCommand;
import com.biopet.facturacion.service.command.EmitirFacturaCommand;
import com.biopet.facturacion.service.command.PagoBorradorCommand;
import com.biopet.facturacion.sri.RespuestaAutorizacionSri;
import com.biopet.facturacion.sri.ResultadoSriFactura;
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
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.anything;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

/**
 * Correccion pre-commit de la Fase 7: {@code XML_AUTORIZADO} debe archivar la
 * UNIDAD DE AUTORIZACION completa que devuelve el SRI -estado, numero, fecha,
 * ambiente, comprobante y mensajes-, no solo los bytes internos de
 * {@code <comprobante>}.
 *
 * <p>Antes de esta correccion, {@code FacturaSriEstadoService.persistirXmlAutorizado}
 * guardaba UNICAMENTE {@code respuesta.comprobante()} tal cual, con lo que
 * {@code numeroAutorizacion}, {@code fechaAutorizacion}, {@code ambiente} y los
 * {@code mensajes} de la respuesta de autorizacion no quedaban archivados en
 * ningun sitio salvo la bitacora JSONB de {@code factura_eventos_sri} -que no
 * es el documento fiscal-. Estos tests fijan el comportamiento correcto.
 */
@SpringBootTest
class XmlAutorizadoIntegrationTest extends FacturaEscenarioTestBase {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 20);
    private static final Path P12 = Path.of("target", "tmp", "xml-autorizado.p12");
    private static final String FECHA_CRUDA_SRI = "2026-09-20T10:15:00-05:00";

    @DynamicPropertySource
    static void configuracion(DynamicPropertyRegistry registry) throws Exception {
        CertificadoPruebaFactory.valido(P12);
        registry.add("sri.firma.certificado.path", P12::toString);
        registry.add("sri.firma.certificado.password", () -> CertificadoPruebaFactory.PASSWORD);
        registry.add("sri.soap.recepcion-url",
                () -> "http://localhost:1/RecepcionComprobantesOffline");
        registry.add("sri.soap.autorizacion-url",
                () -> "http://localhost:1/AutorizacionComprobantesOffline");
    }

    private static Long conceptoCompartidoId;

    @Autowired FacturaEmisionService emisionService;
    @Autowired FacturaXmlService xmlService;
    @Autowired FacturaFirmaService firmaService;
    @Autowired FacturaSriService sriService;
    @Autowired FacturaSriEstadoService estadoService;

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
    // AUT: la unidad completa, no solo el comprobante interno
    // ==================================================================

    @Test
    void unAutArchivaLaUnidadCompletaDeAutorizacionConTodosSusCampos() {
        Factura factura = preparada();
        String clave = factura.getClaveAcceso();
        Instant fechaEsperada = OffsetDateTime.parse(FECHA_CRUDA_SRI).toInstant();

        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything())
                .andRespond(withPayload(autorizadaConAdvertencia(clave, clave, FECHA_CRUDA_SRI)));

        ResultadoSriFactura resultado = sriService.enviar(factura.getId());
        assertThat(resultado.autorizada()).isTrue();

        // Exactamente 1 XML_AUTORIZADO.
        List<FacturaDocumento> autorizados = facturaDocumentoRepository
                .findAllByFactura_Id(factura.getId()).stream()
                .filter(d -> d.getTipo() == TipoDocumentoFactura.XML_AUTORIZADO)
                .toList();
        assertThat(autorizados).hasSize(1);
        FacturaDocumento xmlAutorizado = autorizados.get(0);

        // SHA-256 correcto sobre los bytes exactos guardados.
        assertThat(xmlAutorizado.getSha256())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(FacturaXmlService.sha256(xmlAutorizado.getContenido()));
        assertThat(xmlAutorizado.getBytes()).isEqualTo(xmlAutorizado.getContenido().length);

        // Root = autorizacion.
        Document dom = parsear(xmlAutorizado.getContenido());
        assertThat(dom.getDocumentElement().getTagName()).isEqualTo("autorizacion");

        String texto = new String(xmlAutorizado.getContenido(), StandardCharsets.UTF_8);
        assertThat(texto)
                .as("numeroAutorizacion")
                .contains("<numeroAutorizacion>" + clave + "</numeroAutorizacion>");
        assertThat(texto)
                .as("fechaAutorizacion, convertida a instante pero presente")
                .contains("<fechaAutorizacion>" + fechaEsperada + "</fechaAutorizacion>");
        assertThat(texto).as("ambiente").contains("<ambiente>PRUEBAS</ambiente>");
        assertThat(texto)
                .as("el comprobante que devolvio el SRI, integro")
                .contains("<claveAcceso>" + clave + "</claveAcceso>");
        assertThat(texto)
                .as("los mensajes/advertencias de la respuesta")
                .contains("<identificador>60</identificador>")
                .contains("AUTORIZADO CON OBSERVACIONES")
                .contains("ADVERTENCIA");

        // Nada de SOAP Envelope archivado.
        assertThat(texto).doesNotContain("Envelope").doesNotContain("soap:");

        // UTF-8 explicito y declaracion XML.
        assertThat(texto).startsWith("<?xml").contains("UTF-8");

        // Y los documentos previos, intactos.
        assertThat(facturaDocumentoRepository.findAllByFactura_Id(factura.getId())).hasSize(3);

        verificarSri();
    }

    // ==================================================================
    // Persistencia real en BYTEA
    // ==================================================================

    @Test
    void elXmlAutorizadoReleidoDirectamenteDeBYTEASigueSiendoXmlValido() {
        Factura factura = preparada();
        String clave = factura.getClaveAcceso();

        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything())
                .andRespond(withPayload(autorizadaConAdvertencia(clave, clave, FECHA_CRUDA_SRI)));
        sriService.enviar(factura.getId());

        // Lectura DIRECTA de la columna BYTEA, sin pasar por el cache de
        // primer nivel de Hibernate: es el mismo mecanismo que usaria
        // cualquier proceso externo que releyera el documento archivado.
        byte[] crudo = jdbc.queryForObject(
                "SELECT contenido FROM factura_documentos WHERE factura_id = ? AND tipo = 'XML_AUTORIZADO'",
                byte[].class, factura.getId());

        assertThat(crudo).isNotEmpty();
        // parsear() ya lanza AssertionError si el BYTEA releido no fuera XML
        // bien formado; que no lance es en si mismo la comprobacion pedida.
        Document dom = parsear(crudo);
        assertThat(dom.getDocumentElement().getTagName()).isEqualTo("autorizacion");

        String hashAlmacenado = jdbc.queryForObject(
                "SELECT sha256 FROM factura_documentos WHERE factura_id = ? AND tipo = 'XML_AUTORIZADO'",
                String.class, factura.getId());
        assertThat(hashAlmacenado).isEqualTo(FacturaXmlService.sha256(crudo));

        verificarSri();
    }

    // ==================================================================
    // NAT / PPR: nunca producen XML_AUTORIZADO
    // ==================================================================

    @Test
    void unNoAutorizadoNoCreaXmlAutorizado() {
        Factura factura = preparada();

        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(noAutorizada()));

        sriService.enviar(factura.getId());

        assertThat(facturaDocumentoRepository.findByFactura_IdAndTipo(
                factura.getId(), TipoDocumentoFactura.XML_AUTORIZADO)).isEmpty();
        verificarSri();
    }

    @Test
    void unEnProcesamientoNoCreaXmlAutorizado() {
        Factura factura = preparada();

        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(enProcesamiento()));

        sriService.enviar(factura.getId());

        assertThat(facturaDocumentoRepository.findByFactura_IdAndTipo(
                factura.getId(), TipoDocumentoFactura.XML_AUTORIZADO)).isEmpty();
        verificarSri();
    }

    // ==================================================================
    // Idempotencia: la misma autorizacion, procesada dos veces
    // ==================================================================

    @Test
    void procesarLaMismaAutorizacionDosVecesNoDuplicaNiAlteraElXmlAutorizado() {
        Factura factura = preparada();
        String clave = factura.getClaveAcceso();
        RespuestaAutorizacionSri respuesta = autorizacionAut(clave, clave, FECHA_CRUDA_SRI, List.of());

        estadoService.registrarAutorizacion(factura.getId(), respuesta);
        FacturaDocumento primero = facturaDocumentoRepository
                .findByFactura_IdAndTipo(factura.getId(), TipoDocumentoFactura.XML_AUTORIZADO)
                .orElseThrow();

        // La MISMA respuesta, otra vez: la factura ya esta AUTORIZADA, pero el
        // AUT se sigue contrastando contra lo archivado (ver registrarAutorizacion).
        estadoService.registrarAutorizacion(factura.getId(), respuesta);

        List<FacturaDocumento> todos = facturaDocumentoRepository.findAllByFactura_Id(factura.getId())
                .stream().filter(d -> d.getTipo() == TipoDocumentoFactura.XML_AUTORIZADO).toList();

        assertThat(todos).hasSize(1);
        FacturaDocumento segundo = todos.get(0);
        assertThat(segundo.getId()).isEqualTo(primero.getId());
        assertThat(segundo.getSha256()).isEqualTo(primero.getSha256());
        assertThat(segundo.getContenido()).isEqualTo(primero.getContenido());
    }

    // ==================================================================
    // Inconsistencia: una segunda AUT que contradice la archivada
    // ==================================================================

    @Test
    void unaSegundaAutorizacionQueContradiceLaArchivadaFallaEnLugarDeSobrescribir() {
        Factura factura = preparada();
        String clave = factura.getClaveAcceso();

        estadoService.registrarAutorizacion(factura.getId(),
                autorizacionAut(clave, clave, FECHA_CRUDA_SRI, List.of()));
        FacturaDocumento archivadoAntes = facturaDocumentoRepository
                .findByFactura_IdAndTipo(factura.getId(), TipoDocumentoFactura.XML_AUTORIZADO)
                .orElseThrow();

        // Una segunda respuesta AUT para la MISMA clave, con un comprobante
        // interno DISTINTO: exactamente el tipo de anomalia que no debe
        // resolverse sobrescribiendo en silencio.
        RespuestaAutorizacionSri contradictoria = autorizacionAut(clave, clave, FECHA_CRUDA_SRI,
                List.of(), "<factura id=\"comprobante\" version=\"2.1.0\">"
                        + "<claveAcceso>0000000000000000000000000000000000000000000</claveAcceso>"
                        + "</factura>");

        assertThatThrownBy(() ->
                estadoService.registrarAutorizacion(factura.getId(), contradictoria))
                .isInstanceOf(AutorizacionSriInconsistenteException.class)
                .hasMessageContaining(String.valueOf(factura.getId()));

        // El documento archivado no cambio ni un byte.
        FacturaDocumento archivadoDespues = facturaDocumentoRepository
                .findByFactura_IdAndTipo(factura.getId(), TipoDocumentoFactura.XML_AUTORIZADO)
                .orElseThrow();
        assertThat(archivadoDespues.getId()).isEqualTo(archivadoAntes.getId());
        assertThat(archivadoDespues.getContenido()).isEqualTo(archivadoAntes.getContenido());
        assertThat(archivadoDespues.getSha256()).isEqualTo(archivadoAntes.getSha256());
    }

    @Test
    void unNumeroDeAutorizacionDistintoParaLaMismaClaveTambienEsInconsistencia() {
        Factura factura = preparada();
        String clave = factura.getClaveAcceso();

        estadoService.registrarAutorizacion(factura.getId(),
                autorizacionAut(clave, clave, FECHA_CRUDA_SRI, List.of()));

        RespuestaAutorizacionSri conOtroNumero = autorizacionAut(
                clave, "9999999999999999999999999999999999999999999999", FECHA_CRUDA_SRI, List.of());

        assertThatThrownBy(() ->
                estadoService.registrarAutorizacion(factura.getId(), conOtroNumero))
                .isInstanceOf(AutorizacionSriInconsistenteException.class);
    }

    @Test
    void mensajesDistintosEnUnaConsultaPosteriorNoSonInconsistencia() {
        // Los mensajes NO son un campo material: una segunda consulta puede
        // traer mensajes distintos (o ninguno) sin que eso contradiga la
        // autorizacion ya archivada.
        Factura factura = preparada();
        String clave = factura.getClaveAcceso();

        estadoService.registrarAutorizacion(factura.getId(), autorizacionAut(clave, clave,
                FECHA_CRUDA_SRI, List.of()));
        FacturaDocumento archivadoAntes = facturaDocumentoRepository
                .findByFactura_IdAndTipo(factura.getId(), TipoDocumentoFactura.XML_AUTORIZADO)
                .orElseThrow();

        assertThatCode(() -> estadoService.registrarAutorizacion(factura.getId(),
                autorizacionAut(clave, clave, FECHA_CRUDA_SRI,
                        List.of(new com.biopet.facturacion.sri.MensajeSri(
                                "60", "AUTORIZADO CON OBSERVACIONES", null, "ADVERTENCIA")))))
                .doesNotThrowAnyException();

        FacturaDocumento archivadoDespues = facturaDocumentoRepository
                .findByFactura_IdAndTipo(factura.getId(), TipoDocumentoFactura.XML_AUTORIZADO)
                .orElseThrow();
        // Se conserva el primer documento archivado, sin reemplazarlo.
        assertThat(archivadoDespues.getId()).isEqualTo(archivadoAntes.getId());
        assertThat(archivadoDespues.getContenido()).isEqualTo(archivadoAntes.getContenido());
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    private Factura preparada() {
        Factura emitida = new Escenario().emitir();
        xmlService.generarXml(emitida.getId());
        firmaService.firmarFactura(emitida.getId());
        return facturaRepository.findById(emitida.getId()).orElseThrow();
    }

    private void verificarSri() {
        sriRecepcion.verify();
        sriAutorizacion.verify();
    }

    private static Document parsear(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new AssertionError("El XML_AUTORIZADO no es XML bien formado.", e);
        }
    }

    private static RespuestaAutorizacionSri autorizacionAut(String claveConsultada, String numero,
                                                            String fechaCruda,
                                                            List<com.biopet.facturacion.sri.MensajeSri> mensajes) {
        return autorizacionAut(claveConsultada, numero, fechaCruda, mensajes,
                "<factura id=\"comprobante\" version=\"2.1.0\"><claveAcceso>" + claveConsultada
                        + "</claveAcceso></factura>");
    }

    private static RespuestaAutorizacionSri autorizacionAut(String claveConsultada, String numero,
                                                            String fechaCruda,
                                                            List<com.biopet.facturacion.sri.MensajeSri> mensajes,
                                                            String comprobante) {
        return new RespuestaAutorizacionSri(
                EstadoAutorizacionSri.AUT,
                numero,
                OffsetDateTime.parse(fechaCruda).toInstant(),
                "PRUEBAS",
                comprobante,
                mensajes,
                10L);
    }

    // ------------------------------------------------------------------
    // Respuestas del SRI simulado, en la forma cualificada correcta (ver
    // SriBindingContraWsdlTest: autorizacion y mensaje son elementos GLOBALES).
    // ------------------------------------------------------------------

    private static StringSource recibida() {
        return new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante>
                    <estado>RECIBIDA</estado><comprobantes/>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """);
    }

    private static StringSource autorizadaConAdvertencia(String claveConsultada, String numero,
                                                         String fechaCruda) {
        return new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <claveAccesoConsultada>%s</claveAccesoConsultada>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones>
                      <ns:autorizacion>
                        <estado>AUTORIZADO</estado>
                        <numeroAutorizacion>%s</numeroAutorizacion>
                        <fechaAutorizacion>%s</fechaAutorizacion>
                        <ambiente>PRUEBAS</ambiente>
                        <comprobante>&lt;factura id="comprobante" version="2.1.0"&gt;&lt;claveAcceso&gt;%s&lt;/claveAcceso&gt;&lt;/factura&gt;</comprobante>
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
                """.formatted(claveConsultada, numero, fechaCruda, claveConsultada));
    }

    private static StringSource noAutorizada() {
        return new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones><ns:autorizacion>
                      <estado>NO AUTORIZADO</estado>
                      <mensajes><ns:mensaje><identificador>39</identificador>
                        <mensaje>FIRMA INVALIDA</mensaje></ns:mensaje></mensajes>
                    </ns:autorizacion></autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """);
    }

    private static StringSource enProcesamiento() {
        return new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones><ns:autorizacion>
                      <estado>EN PROCESAMIENTO</estado>
                    </ns:autorizacion></autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """);
    }

    // ==================================================================
    // Escenario
    // ==================================================================

    private final class Escenario {
        private final EmisorFiscal emisor = nuevoEmisor();
        private final PuntoEmision punto = nuevoPunto(emisor);
        private final Usuario usuario = nuevoUsuario();
        private final DatosFacturacion datos;

        Escenario() {
            nuevoContador(punto, AmbienteSri.PRUEBAS, 0L);
            datos = nuevosDatos(usuario, TipoIdentificacionSri.CEDULA, "0000000000",
                    "PERSONA FICTICIA");
        }

        Factura emitir() {
            ConceptoFacturable concepto = conceptoFacturableRepository
                    .findById(conceptoCompartidoId).orElseThrow();
            Factura borrador = borradorService.crear(
                    new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));
            borradorService.seleccionarComprador(borrador.getId(), datos.getId());
            borradorService.reemplazarDetalles(borrador.getId(),
                    List.of(DetalleBorradorCommand.de(concepto.getId(), new BigDecimal("2"))));
            borradorService.reemplazarPagos(borrador.getId(), List.of(
                    PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("46.00"))));
            return emisionService.emitir(new EmitirFacturaCommand(
                    borrador.getId(), punto.getId(), AmbienteSri.PRUEBAS));
        }
    }
}
