package com.biopet.facturacion.persistence;

import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.EstadoAutorizacionSri;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.EstadoRecepcionSri;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.FacturaEventoSri;
import com.biopet.facturacion.entity.OperacionSri;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.ResultadoEventoSri;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.entity.TipoIdentificacionSri;
import com.biopet.facturacion.exception.FacturaNoEnviableException;
import com.biopet.facturacion.exception.FacturaXmlInvalidoException;
import com.biopet.facturacion.exception.FirmaElectronicaException;
import com.biopet.facturacion.firma.CertificadoPruebaFactory;
import com.biopet.facturacion.repository.FacturaEventoSriRepository;
import com.biopet.facturacion.service.FacturaEmisionService;
import com.biopet.facturacion.service.FacturaFirmaService;
import com.biopet.facturacion.service.FacturaSriService;
import com.biopet.facturacion.service.FacturaXmlService;
import com.biopet.facturacion.service.command.CrearFacturaBorradorCommand;
import com.biopet.facturacion.service.command.DetalleBorradorCommand;
import com.biopet.facturacion.service.command.EmitirFacturaCommand;
import com.biopet.facturacion.service.command.PagoBorradorCommand;
import com.biopet.facturacion.sri.ResultadoSriFactura;
import com.biopet.facturacion.sri.SriComunicacionException;
import com.biopet.facturacion.sri.TipoFalloSri;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.ws.test.client.RequestMatcher;
import org.springframework.xml.transform.StringSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.anything;
import static org.springframework.ws.test.client.ResponseCreators.withException;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;
import static org.springframework.ws.test.client.ResponseCreators.withServerOrReceiverFault;

/**
 * Pipeline SRI completo contra PostgreSQL real y un SRI simulado:
 * EMITIDA + XML_FIRMADO -&gt; recepcion -&gt; autorizacion -&gt; estado persistido.
 *
 * <h2>Ni un byte sale a Internet</h2>
 *
 * <p>Los dos {@code WebServiceTemplate} del contexto se envuelven con
 * {@link MockWebServiceServer}, que sustituye el transporte. Eso permite
 * reproducir a voluntad lo que el SRI real no ofrece cuando hace falta -un
 * timeout, un SOAP Fault, un PPR que luego pasa a AUT- y deja la suite
 * ejecutable en un CI sin salida a la red.
 *
 * <h2>Lo que estos tests protegen</h2>
 *
 * <p>Sobre todo, tres cosas que un error aqui convertiria en un problema
 * fiscal real:
 * <ul>
 *   <li>que AUTORIZADA solo se alcance con un AUT, nunca por un HTTP 200 ni por
 *       una recepcion aceptada;</li>
 *   <li>que ningun reintento genere clave, secuencial o firma nuevos;</li>
 *   <li>que ninguna llamada al SRI ocurra con una transaccion de PostgreSQL
 *       abierta.</li>
 * </ul>
 */
@SpringBootTest
class FacturaSriServiceIntegrationTest extends FacturaEscenarioTestBase {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 15);
    private static final Path P12 = Path.of("target", "tmp", "firma-sri.p12");

    @DynamicPropertySource
    static void configuracion(DynamicPropertyRegistry registry) throws Exception {
        CertificadoPruebaFactory.valido(P12);
        registry.add("sri.firma.certificado.path", P12::toString);
        registry.add("sri.firma.certificado.password", () -> CertificadoPruebaFactory.PASSWORD);
        // Endpoints locales e inalcanzables: aunque el mock fallara, jamas se
        // hablaria con el SRI de verdad desde la suite.
        registry.add("sri.soap.recepcion-url",
                () -> "http://localhost:1/RecepcionComprobantesOffline");
        registry.add("sri.soap.autorizacion-url",
                () -> "http://localhost:1/AutorizacionComprobantesOffline");
    }

    /**
     * Concepto y tarifa se crean UNA vez para toda la clase. Los codigos de
     * porcentaje de prueba son un recurso escaso (dos digitos, compartidos por
     * todas las clases del modulo) y esta clase tiene muchos tests; gastar uno
     * por test los agotaria.
     */
    private static Long conceptoCompartidoId;

    @Autowired FacturaEmisionService emisionService;
    @Autowired FacturaXmlService xmlService;
    @Autowired FacturaFirmaService firmaService;
    @Autowired FacturaSriService sriService;
    @Autowired FacturaEventoSriRepository eventoRepository;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper objectMapper;

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
    // 1. Camino feliz: RECIBIDA -> AUT
    // ==================================================================

    @Test
    void recibidaSeguidaDeAutorizadoDejaLaFacturaAutorizada() {
        Factura factura = preparada();
        String clave = factura.getClaveAcceso();
        Long secuencial = factura.getSecuencial();
        byte[] firmadoAntes = documento(factura, TipoDocumentoFactura.XML_FIRMADO).getContenido();

        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(autorizado(clave)));

        ResultadoSriFactura resultado = sriService.enviar(factura.getId());

        assertThat(resultado.autorizada()).isTrue();
        assertThat(resultado.pendiente()).isFalse();

        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.AUTORIZADA);
        assertThat(releida.getEstadoRecepcion()).isEqualTo(EstadoRecepcionSri.RECIBIDA);
        assertThat(releida.getEstadoAutorizacion()).isEqualTo(EstadoAutorizacionSri.AUT);
        assertThat(releida.getNumeroAutorizacion()).isEqualTo(clave);
        assertThat(releida.getFechaAutorizacion()).isNotNull();
        assertThat(releida.getProximoIntentoEn()).isNull();
        assertThat(releida.getIntentosAutorizacion()).isEqualTo(1);

        // Autorizar no renumera ni vuelve a firmar.
        assertThat(releida.getClaveAcceso()).isEqualTo(clave);
        assertThat(releida.getSecuencial()).isEqualTo(secuencial);
        assertThat(documento(releida, TipoDocumentoFactura.XML_FIRMADO).getContenido())
                .isEqualTo(firmadoAntes);

        verificarSri();
    }

    // ==================================================================
    // 2. DEVUELTA con varios mensajes
    // ==================================================================

    @Test
    void devueltaConservaTodosLosMensajesYNoConsultaAutorizacion() {
        Factura factura = preparada();

        sriRecepcion.expect(anything()).andRespond(withPayload(new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante>
                    <estado>DEVUELTA</estado>
                    <comprobantes><ns:comprobante>
                      <mensajes>
                        <ns:mensaje><identificador>39</identificador><mensaje>FIRMA INVALIDA</mensaje>
                          <informacionAdicional>Certificado no confiable</informacionAdicional>
                          <tipo>ERROR</tipo></ns:mensaje>
                        <ns:mensaje><identificador>52</identificador>
                          <mensaje>ERROR EN LA ESTRUCTURA</mensaje><tipo>ERROR</tipo></ns:mensaje>
                        <ns:mensaje><identificador>60</identificador>
                          <mensaje>ADVERTENCIA SECUNDARIA</mensaje><tipo>ADVERTENCIA</tipo></ns:mensaje>
                      </mensajes>
                    </ns:comprobante></comprobantes>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """)));
        // Sin expectativas en autorizacion: una devolucion real no se consulta.

        ResultadoSriFactura resultado = sriService.enviar(factura.getId());

        assertThat(resultado.estado()).isEqualTo(EstadoFactura.RECHAZADA);
        assertThat(resultado.mensajes()).hasSize(3);

        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getEstadoRecepcion()).isEqualTo(EstadoRecepcionSri.DEVUELTA);
        assertThat(releida.getEstadoAutorizacion()).isNull();
        assertThat(releida.getIntentosAutorizacion()).isZero();
        // Rechazada, pero conserva numeracion y documentos: el comprobante
        // existio y el secuencial se consumio.
        assertThat(releida.getClaveAcceso()).isEqualTo(factura.getClaveAcceso());
        assertThat(releida.getSecuencial()).isEqualTo(factura.getSecuencial());
        assertThat(facturaDocumentoRepository.findAllByFactura_Id(factura.getId())).hasSize(2);

        // Los tres mensajes, con su informacion adicional, en la bitacora JSONB.
        // Se parsea en lugar de comparar texto: PostgreSQL normaliza el JSONB
        // (reordena claves y anade espacios), y afirmar sobre su formato exacto
        // seria probar a PostgreSQL, no a BIOPET.
        JsonNode bitacora = jsonDelEvento(factura, OperacionSri.RECEPCION);
        assertThat(bitacora).hasSize(3);
        assertThat(bitacora).extracting(m -> m.get("identificador").asText())
                .containsExactlyInAnyOrder("39", "52", "60");
        assertThat(bitacora.get(0).get("informacionAdicional").asText())
                .isEqualTo("Certificado no confiable");
        assertThat(bitacora.get(2).get("tipo").asText()).isEqualTo("ADVERTENCIA");

        verificarSri();
    }

    // ==================================================================
    // 3. RECIBIDA -> NAT
    // ==================================================================

    @Test
    void noAutorizadoDejaLaFacturaRechazadaSinBorrarDocumentos() {
        Factura factura = preparada();

        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones><ns:autorizacion>
                      <estado>NO AUTORIZADO</estado>
                      <mensajes><ns:mensaje><identificador>39</identificador>
                        <mensaje>FIRMA INVALIDA</mensaje><tipo>ERROR</tipo></ns:mensaje></mensajes>
                    </ns:autorizacion></autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """)));

        ResultadoSriFactura resultado = sriService.enviar(factura.getId());

        assertThat(resultado.estado()).isEqualTo(EstadoFactura.RECHAZADA);
        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getEstadoRecepcion()).isEqualTo(EstadoRecepcionSri.RECIBIDA);
        assertThat(releida.getEstadoAutorizacion()).isEqualTo(EstadoAutorizacionSri.NAT);
        assertThat(releida.getNumeroAutorizacion()).isNull();
        assertThat(releida.getProximoIntentoEn()).isNull();

        // Los documentos anteriores siguen ahi; no se guarda XML_AUTORIZADO.
        assertThat(facturaDocumentoRepository.findAllByFactura_Id(factura.getId())).hasSize(2);
        assertThat(facturaDocumentoRepository
                .findByFactura_IdAndTipo(factura.getId(), TipoDocumentoFactura.XML_AUTORIZADO))
                .isEmpty();

        verificarSri();
    }

    // ==================================================================
    // 4. RECIBIDA -> PPR
    // ==================================================================

    @Test
    void pprNoDejaLaFacturaNiAutorizadaNiRechazadaYProgramaElSiguienteIntento() {
        Factura factura = preparada();

        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(enProcesamiento()));

        ResultadoSriFactura resultado = sriService.enviar(factura.getId());

        assertThat(resultado.pendiente()).isTrue();
        assertThat(resultado.autorizada()).isFalse();

        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(releida.getEstadoRecepcion()).isEqualTo(EstadoRecepcionSri.RECIBIDA);
        assertThat(releida.getEstadoAutorizacion()).isEqualTo(EstadoAutorizacionSri.PPR);
        assertThat(releida.getNumeroAutorizacion()).isNull();
        assertThat(releida.getIntentosAutorizacion()).isEqualTo(1);
        assertThat(releida.getProximoIntentoEn()).isNotNull();

        verificarSri();
    }

    // ==================================================================
    // 5 y 6. Timeouts
    // ==================================================================

    @Test
    void unTimeoutEnRecepcionNoRechazaNadaYDejaLaFacturaReintentable() {
        Factura factura = preparada();
        byte[] firmadoAntes = documento(factura, TipoDocumentoFactura.XML_FIRMADO).getContenido();

        sriRecepcion.expect(anything())
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> sriService.enviar(factura.getId()))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.TIMEOUT);

        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(releida.getEstadoRecepcion()).isNull();
        assertThat(releida.getEstadoAutorizacion()).isNull();
        assertThat(releida.getClaveAcceso()).isEqualTo(factura.getClaveAcceso());
        assertThat(releida.getSecuencial()).isEqualTo(factura.getSecuencial());
        assertThat(documento(releida, TipoDocumentoFactura.XML_FIRMADO).getContenido())
                .isEqualTo(firmadoAntes);
        assertThat(releida.getProximoIntentoEn()).isNotNull();
        // El contador es de intentos de AUTORIZACION: un timeout de recepcion no
        // lo mueve.
        assertThat(releida.getIntentosAutorizacion()).isZero();

        FacturaEventoSri evento = ultimoEvento(factura, OperacionSri.RECEPCION);
        assertThat(evento.getResultado()).isEqualTo(ResultadoEventoSri.TIMEOUT);
        // Un timeout no trajo cuerpo: la columna JSONB queda nula, que es
        // exactamente lo que significa.
        assertThat(evento.getMensajes()).isNull();

        verificarSri();
    }

    @Test
    void unTimeoutEnAutorizacionConservaLaRecepcionYaConseguida() {
        Factura factura = preparada();

        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything())
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> sriService.enviar(factura.getId()))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.TIMEOUT);

        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        // Ni AUTORIZADA ni RECHAZADA: la recepcion si consta, la autorizacion no.
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(releida.getEstadoRecepcion()).isEqualTo(EstadoRecepcionSri.RECIBIDA);
        assertThat(releida.getEstadoAutorizacion()).isNull();
        assertThat(releida.getIntentosAutorizacion()).isEqualTo(1);

        assertThat(ultimoEvento(factura, OperacionSri.AUTORIZACION).getResultado())
                .isEqualTo(ResultadoEventoSri.TIMEOUT);

        verificarSri();
    }

    // ==================================================================
    // 7 y 8. SOAP Fault y respuesta ininteligible
    // ==================================================================

    @Test
    void unSoapFaultSeRegistraComoErrorTecnicoYNoComoRechazo() {
        Factura factura = preparada();

        sriRecepcion.expect(anything())
                .andRespond(withServerOrReceiverFault("Servicio no disponible", Locale.getDefault()));

        assertThatThrownBy(() -> sriService.enviar(factura.getId()))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.SOAP_FAULT);

        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(releida.getEstadoRecepcion()).isNull();

        FacturaEventoSri evento = ultimoEvento(factura, OperacionSri.RECEPCION);
        assertThat(evento.getResultado()).isEqualTo(ResultadoEventoSri.ERROR_TECNICO);
        // El diagnostico si se guarda, pero como mensaje; el sobre SOAP no.
        assertThat(evento.getMensajes())
                .contains("SOAP_FAULT")
                .doesNotContain("Envelope");

        verificarSri();
    }

    @Test
    void unaRespuestaFueraDelContratoEsErrorTecnicoYNoUnRechazoInventado() {
        Factura factura = preparada();

        sriRecepcion.expect(anything()).andRespond(withPayload(new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante><estado>PERPLEJA</estado></RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """)));

        assertThatThrownBy(() -> sriService.enviar(factura.getId()))
                .isInstanceOf(SriComunicacionException.class)
                .extracting(e -> ((SriComunicacionException) e).getTipo())
                .isEqualTo(TipoFalloSri.RESPUESTA_INVALIDA);

        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(ultimoEvento(factura, OperacionSri.RECEPCION).getResultado())
                .isEqualTo(ResultadoEventoSri.ERROR_TECNICO);

        verificarSri();
    }

    // ==================================================================
    // 9. Integridad local: si falla, NO se llama al SRI
    // ==================================================================

    @Test
    void unXmlFirmadoCorruptoNoLlegaASalirALaRed() {
        Factura factura = preparada();

        // Se corrompen los bytes dejando el hash antiguo.
        jdbc.update("UPDATE factura_documentos SET contenido = ?::bytea "
                        + "WHERE factura_id = ? AND tipo = 'XML_FIRMADO'",
                "\\x00010203", factura.getId());

        assertThatThrownBy(() -> sriService.enviar(factura.getId()))
                .isInstanceOf(FacturaXmlInvalidoException.class)
                .hasMessageContaining("corrupto");

        // Sin expectativas declaradas: si se hubiera llamado al SRI, el mock
        // habria fallado. verify() confirma ademas que no quedo nada pendiente.
        verificarSri();
        assertThat(eventoRepository.findAllByFactura_IdOrderByCreadoEnDesc(factura.getId()))
                .isEmpty();
    }

    @Test
    void unXmlAlteradoDespuesDeFirmarTampocoSeEnvia() {
        Factura factura = preparada();

        // Se altera el contenido Y se recalcula el hash: la integridad de
        // almacenamiento cuadra, pero la firma XAdES ya no cubre estos bytes.
        FacturaDocumento firmado = documento(factura, TipoDocumentoFactura.XML_FIRMADO);
        byte[] alterado = new String(firmado.getContenido(), StandardCharsets.UTF_8)
                .replace("PERSONA FICTICIA", "PERSONA SUPLANTADA")
                .getBytes(StandardCharsets.UTF_8);
        jdbc.update("UPDATE factura_documentos SET contenido = ?, sha256 = ? "
                        + "WHERE factura_id = ? AND tipo = 'XML_FIRMADO'",
                alterado, FacturaXmlService.sha256(alterado), factura.getId());

        assertThatThrownBy(() -> sriService.enviar(factura.getId()))
                .isInstanceOf(FirmaElectronicaException.class);

        verificarSri();
    }

    // ==================================================================
    // 10. Idempotencia sobre una factura ya AUTORIZADA
    // ==================================================================

    @Test
    void unaFacturaAutorizadaNiSeReenviaNiSeVuelveAConsultar() {
        Factura factura = preparada();
        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything())
                .andRespond(withPayload(autorizado(factura.getClaveAcceso())));
        sriService.enviar(factura.getId());
        verificarSri();

        // A partir de aqui, ninguna expectativa: cualquier llamada al SRI
        // haria fallar el mock.
        sriRecepcion.reset();
        sriAutorizacion.reset();

        ResultadoSriFactura reenvio = sriService.enviar(factura.getId());
        ResultadoSriFactura resincronizacion = sriService.sincronizar(factura.getId());

        assertThat(reenvio.autorizada()).isTrue();
        assertThat(resincronizacion.autorizada()).isTrue();
        assertThat(reenvio.numeroAutorizacion()).isEqualTo(factura.getClaveAcceso());

        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getIntentosAutorizacion())
                .as("una factura ya autorizada no consume intentos")
                .isEqualTo(1);
        assertThat(eventoRepository.findAllByFactura_IdOrderByCreadoEnDesc(factura.getId()))
                .hasSize(2);

        verificarSri();
    }

    // ==================================================================
    // 11. sincronizar() resuelve una pendiente
    // ==================================================================

    @Test
    void sincronizarUnaPendienteLaLlevaDePprAAutorizada() {
        Factura factura = preparada();
        // Todas las respuestas se programan por adelantado y EN ORDEN: primero
        // el SRI la recibe y la deja en proceso; mas tarde ya la ha resuelto.
        // sincronizar() NO reenvia el comprobante, solo pregunta por la misma
        // clave, y por eso no hay una segunda respuesta de recepcion.
        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(enProcesamiento()));
        sriAutorizacion.expect(anything())
                .andRespond(withPayload(autorizado(factura.getClaveAcceso())));

        assertThat(sriService.enviar(factura.getId()).pendiente()).isTrue();

        ResultadoSriFactura resultado = sriService.sincronizar(factura.getId());

        assertThat(resultado.autorizada()).isTrue();
        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.AUTORIZADA);
        assertThat(releida.getEstadoAutorizacion()).isEqualTo(EstadoAutorizacionSri.AUT);
        assertThat(releida.getIntentosAutorizacion()).isEqualTo(2);
        assertThat(releida.getProximoIntentoEn()).isNull();

        // Una sola llamada a recepcion en todo el escenario.
        assertThat(eventoRepository.findAllByFactura_IdAndOperacionOrderByCreadoEnDesc(
                factura.getId(), OperacionSri.RECEPCION)).hasSize(1);
        verificarSri();
    }

    // ==================================================================
    // 12. Clave ya registrada -> consultar, nunca renumerar
    // ==================================================================

    @Test
    void claveYaRegistradaConsultaAutorizacionEnLugarDeGenerarOtraClave() {
        Factura factura = preparada();
        String clave = factura.getClaveAcceso();

        sriRecepcion.expect(anything()).andRespond(withPayload(new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante>
                    <estado>DEVUELTA</estado>
                    <comprobantes><ns:comprobante><mensajes><ns:mensaje>
                      <identificador>43</identificador>
                      <mensaje>CLAVE ACCESO REGISTRADA</mensaje>
                      <tipo>ERROR</tipo>
                    </ns:mensaje></mensajes></ns:comprobante></comprobantes>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """)));
        sriAutorizacion.expect(anything()).andRespond(withPayload(autorizado(clave)));

        ResultadoSriFactura resultado = sriService.enviar(factura.getId());

        assertThat(resultado.autorizada()).isTrue();

        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.AUTORIZADA);
        // Una devolucion por clave registrada NO es un rechazo.
        assertThat(releida.getClaveAcceso())
                .as("jamas se genera una clave nueva para reintentar")
                .isEqualTo(clave);
        assertThat(releida.getSecuencial()).isEqualTo(factura.getSecuencial());
        // Se conserva lo que el SRI dijo de verdad en recepcion, sin reescribir
        // la historia.
        assertThat(releida.getEstadoRecepcion()).isEqualTo(EstadoRecepcionSri.DEVUELTA);

        verificarSri();
    }

    @Test
    void comprobanteEnProcesamientoNoSeReenviaSinoQueSeConsulta() {
        Factura factura = preparada();

        sriRecepcion.expect(anything()).andRespond(withPayload(new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante>
                    <estado>DEVUELTA</estado>
                    <comprobantes><ns:comprobante><mensajes><ns:mensaje>
                      <identificador>70</identificador>
                      <mensaje>COMPROBANTE EN PROCESAMIENTO</mensaje>
                    </ns:mensaje></mensajes></ns:comprobante></comprobantes>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """)));
        sriAutorizacion.expect(anything()).andRespond(withPayload(enProcesamiento()));

        ResultadoSriFactura resultado = sriService.enviar(factura.getId());

        assertThat(resultado.pendiente()).isTrue();
        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getEstado())
                .as("en procesamiento no es un rechazo")
                .isEqualTo(EstadoFactura.EMITIDA);
        assertThat(releida.getEstadoAutorizacion()).isEqualTo(EstadoAutorizacionSri.PPR);

        // Una sola llamada a recepcion: no se bombardea al SRI.
        assertThat(eventoRepository.findAllByFactura_IdAndOperacionOrderByCreadoEnDesc(
                factura.getId(), OperacionSri.RECEPCION)).hasSize(1);
        verificarSri();
    }

    // ==================================================================
    // 13. Reintento tras timeout: misma clave, mismo secuencial, mismos bytes
    // ==================================================================

    @Test
    void trasUnTimeoutSeConsultaAntesDeReenviarYNuncaCambiaLaNumeracion() {
        Factura factura = preparada();
        String clave = factura.getClaveAcceso();
        Long secuencial = factura.getSecuencial();
        byte[] firmado = documento(factura, TipoDocumentoFactura.XML_FIRMADO).getContenido();

        // Primer intento: timeout en recepcion. No se sabe si llego.
        sriRecepcion.expect(anything())
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThatThrownBy(() -> sriService.enviar(factura.getId()))
                .isInstanceOf(SriComunicacionException.class);
        verificarSri();
        sriRecepcion.reset();
        sriAutorizacion.reset();

        // Segundo intento: lo PRIMERO es preguntar por la misma clave. Como el
        // SRI no la conoce (sin autorizaciones = PPR) y no consta recibida, se
        // reenvia; entonces si la recibe y la autoriza.
        sriAutorizacion.expect(anything()).andRespond(withPayload(sinAutorizaciones()));
        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(autorizado(clave)));

        ResultadoSriFactura resultado = sriService.enviar(factura.getId());

        assertThat(resultado.autorizada()).isTrue();

        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(releida.getClaveAcceso()).isEqualTo(clave);
        assertThat(releida.getSecuencial()).isEqualTo(secuencial);
        assertThat(releida.getNumeroAutorizacion()).isEqualTo(clave);
        // Los mismos bytes firmados en los dos envios: no se refirmo nada.
        assertThat(documento(releida, TipoDocumentoFactura.XML_FIRMADO).getContenido())
                .isEqualTo(firmado);
        // Y un unico secuencial consumido en el punto de emision.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM facturas WHERE clave_acceso = ?", Integer.class, clave))
                .isEqualTo(1);

        verificarSri();
    }

    @Test
    void siTrasUnTimeoutElSriYaLaAutorizoNoSeReenviaNada() {
        Factura factura = preparada();

        sriRecepcion.expect(anything())
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThatThrownBy(() -> sriService.enviar(factura.getId()))
                .isInstanceOf(SriComunicacionException.class);
        verificarSri();
        sriRecepcion.reset();
        sriAutorizacion.reset();

        // La consulta previa revela que si habia llegado y ya esta autorizada:
        // reenviarla habria sido pedir un duplicado.
        sriAutorizacion.expect(anything())
                .andRespond(withPayload(autorizado(factura.getClaveAcceso())));

        ResultadoSriFactura resultado = sriService.enviar(factura.getId());

        assertThat(resultado.autorizada()).isTrue();
        // Una sola llamada a recepcion en toda la historia: la del timeout.
        assertThat(eventoRepository.findAllByFactura_IdAndOperacionOrderByCreadoEnDesc(
                factura.getId(), OperacionSri.RECEPCION)).hasSize(1);

        Factura releida = facturaRepository.findById(factura.getId()).orElseThrow();
        // Un AUT demuestra que el SRI lo recibio, aunque BIOPET no viera la
        // respuesta de recepcion.
        assertThat(releida.getEstadoRecepcion()).isEqualTo(EstadoRecepcionSri.RECIBIDA);

        verificarSri();
    }

    // ==================================================================
    // 14. XML_AUTORIZADO
    // ==================================================================

    @Test
    void elComprobanteAutorizadoSeGuardaAparteYLosAnterioresSiguenIntactos() {
        Factura factura = preparada();
        byte[] generadoAntes = documento(factura, TipoDocumentoFactura.XML_GENERADO).getContenido();
        FacturaDocumento firmadoAntes = documento(factura, TipoDocumentoFactura.XML_FIRMADO);

        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything())
                .andRespond(withPayload(autorizado(factura.getClaveAcceso())));

        sriService.enviar(factura.getId());

        FacturaDocumento autorizado = documento(factura, TipoDocumentoFactura.XML_AUTORIZADO);
        assertThat(autorizado.getSha256())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(FacturaXmlService.sha256(autorizado.getContenido()));
        assertThat(autorizado.getBytes()).isEqualTo(autorizado.getContenido().length);

        String texto = new String(autorizado.getContenido(), StandardCharsets.UTF_8);
        assertThat(texto).contains("<claveAcceso>" + factura.getClaveAcceso() + "</claveAcceso>");
        // El SRI puede reserializar el comprobante: no se asume que coincida
        // byte a byte con el firmado, y aqui deliberadamente no coincide.
        assertThat(autorizado.getContenido()).isNotEqualTo(firmadoAntes.getContenido());

        // Los tres coexisten y los dos anteriores no se han tocado.
        assertThat(facturaDocumentoRepository.findAllByFactura_Id(factura.getId())).hasSize(3);
        assertThat(documento(factura, TipoDocumentoFactura.XML_GENERADO).getContenido())
                .isEqualTo(generadoAntes);
        assertThat(documento(factura, TipoDocumentoFactura.XML_FIRMADO).getSha256())
                .isEqualTo(firmadoAntes.getSha256());

        verificarSri();
    }

    @Test
    void consultarDosVecesUnaAutorizadaNoDuplicaElXmlAutorizado() {
        Factura factura = preparada();
        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(enProcesamiento()));
        sriAutorizacion.expect(anything())
                .andRespond(withPayload(autorizado(factura.getClaveAcceso())));

        sriService.enviar(factura.getId());
        sriService.sincronizar(factura.getId());

        // Y una tercera consulta, sobre una factura ya autorizada: no hay una
        // tercera expectativa, asi que si saliera a la red el mock fallaria.
        // Tampoco intenta insertar otra fila de XML_AUTORIZADO.
        sriService.sincronizar(factura.getId());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM factura_documentos WHERE factura_id = ? "
                        + "AND tipo = 'XML_AUTORIZADO'", Integer.class, factura.getId()))
                .isEqualTo(1);
        verificarSri();
    }

    // ==================================================================
    // 15. Bitacora: un evento por intento, numerado y cronometrado
    // ==================================================================

    @Test
    void cadaIntentoDejaUnEventoNumeradoYConDuracion() {
        Factura factura = preparada();

        sriRecepcion.expect(anything()).andRespond(withPayload(recibida()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(enProcesamiento()));
        sriAutorizacion.expect(anything()).andRespond(withPayload(enProcesamiento()));
        sriAutorizacion.expect(anything())
                .andRespond(withPayload(autorizado(factura.getClaveAcceso())));

        sriService.enviar(factura.getId());
        sriService.sincronizar(factura.getId());
        sriService.sincronizar(factura.getId());

        List<FacturaEventoSri> autorizaciones = eventoRepository
                .findAllByFactura_IdAndOperacionOrderByCreadoEnDesc(
                        factura.getId(), OperacionSri.AUTORIZACION);

        assertThat(autorizaciones).hasSize(3);
        assertThat(autorizaciones).extracting(FacturaEventoSri::getIntento)
                .containsExactly(3, 2, 1);
        assertThat(autorizaciones).extracting(FacturaEventoSri::getResultado)
                .containsExactly(ResultadoEventoSri.AUT, ResultadoEventoSri.PPR,
                        ResultadoEventoSri.PPR);
        assertThat(autorizaciones).allSatisfy(evento ->
                assertThat(evento.getDuracionMs()).isNotNull().isNotNegative());

        // La recepcion tiene su propia numeracion, independiente.
        assertThat(eventoRepository.findAllByFactura_IdAndOperacionOrderByCreadoEnDesc(
                factura.getId(), OperacionSri.RECEPCION))
                .singleElement()
                .satisfies(evento -> assertThat(evento.getIntento()).isEqualTo(1));

        assertThat(facturaRepository.findById(factura.getId()).orElseThrow()
                .getIntentosAutorizacion()).isEqualTo(3);

        verificarSri();
    }

    // ==================================================================
    // Precondiciones: lo que ni siquiera abre una conexion
    // ==================================================================

    @Test
    void unBorradorNoSeEnviaAlSri() {
        Escenario escenario = new Escenario();
        Factura borrador = borradorService.crear(
                new CrearFacturaBorradorCommand(escenario.usuario.getId(), null, FECHA));

        assertThatThrownBy(() -> sriService.enviar(borrador.getId()))
                .isInstanceOf(FacturaNoEnviableException.class)
                .hasMessageContaining("BORRADOR");
        assertThatThrownBy(() -> sriService.sincronizar(borrador.getId()))
                .isInstanceOf(FacturaNoEnviableException.class);

        verificarSri();
    }

    @Test
    void sinXmlFirmadoNoHayNadaQueEnviar() {
        Factura emitida = new Escenario().emitir();
        xmlService.generarXml(emitida.getId());

        assertThatThrownBy(() -> sriService.enviar(emitida.getId()))
                .isInstanceOf(FacturaNoEnviableException.class)
                .hasMessageContaining("XML firmado");

        verificarSri();
    }

    @Test
    void unaFacturaRechazadaNoSeReenvia() {
        Factura factura = preparada();
        sriRecepcion.expect(anything()).andRespond(withPayload(new StringSource("""
                <ns:validarComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.recepcion">
                  <RespuestaRecepcionComprobante>
                    <estado>DEVUELTA</estado>
                    <comprobantes><ns:comprobante><mensajes><ns:mensaje>
                      <identificador>35</identificador>
                      <mensaje>ARCHIVO NO CUMPLE ESTRUCTURA XML</mensaje>
                    </ns:mensaje></mensajes></ns:comprobante></comprobantes>
                  </RespuestaRecepcionComprobante>
                </ns:validarComprobanteResponse>
                """)));
        sriService.enviar(factura.getId());
        verificarSri();

        // Reenviar una rechazada exigiria una clave nueva, y eso es justo lo
        // que este modulo nunca hace.
        assertThatThrownBy(() -> sriService.enviar(factura.getId()))
                .isInstanceOf(FacturaNoEnviableException.class)
                .hasMessageContaining("RECHAZADA");

        verificarSri();
    }

    @Test
    void unaFacturaInexistenteODeIdNuloSeRechazaAntesDeTocarLaRed() {
        assertThatThrownBy(() -> sriService.enviar(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sriService.sincronizar(987_654_321L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verificarSri();
    }

    // ==================================================================
    // 16. Ninguna transaccion de base de datos abierta durante el SOAP
    // ==================================================================

    @Test
    void ningunaLlamadaAlSriOcurreConUnaTransaccionDeBaseDeDatosAbierta() {
        Factura factura = preparada();

        sriRecepcion.expect(sinTransaccionNiConexion("recepcion"))
                .andRespond(withPayload(recibida()));
        sriAutorizacion.expect(sinTransaccionNiConexion("autorizacion"))
                .andRespond(withPayload(autorizado(factura.getClaveAcceso())));

        ResultadoSriFactura resultado = sriService.enviar(factura.getId());

        assertThat(resultado.autorizada()).isTrue();
        verificarSri();
    }

    /**
     * Matcher que se ejecuta EN EL MOMENTO de enviar el mensaje SOAP, es decir,
     * exactamente donde estaria la espera de red.
     *
     * <p>Comprueba tres cosas complementarias:
     * <ul>
     *   <li>no hay transaccion de Spring activa;</li>
     *   <li>no hay recursos transaccionales ligados al hilo -ni EntityManager ni
     *       conexion JDBC-, que es lo que delataria una sesion abierta aunque la
     *       transaccion pareciese cerrada;</li>
     *   <li>el pool no tiene ninguna conexion en uso.</li>
     * </ul>
     *
     * <p>La tercera es la que de verdad importa en produccion: una conexion
     * retenida durante 60 s de espera al SRI es una conexion que nadie mas puede
     * usar, y en el plan gratuito de Render el pool se agota con muy pocas.
     */
    private RequestMatcher sinTransaccionNiConexion(String operacion) {
        return (uri, request) -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("transaccion activa durante la llamada SOAP de %s", operacion)
                    .isFalse();
            assertThat(TransactionSynchronizationManager.getResourceMap())
                    .as("recursos transaccionales ligados al hilo durante la llamada de %s",
                            operacion)
                    .isEmpty();
            if (dataSource instanceof HikariDataSource hikari) {
                assertThat(hikari.getHikariPoolMXBean().getActiveConnections())
                        .as("conexiones del pool en uso durante la llamada de %s", operacion)
                        .isZero();
            }
        };
    }

    // ==================================================================
    // Utilidades
    // ==================================================================

    /** Deja la factura EMITIDA con XML_GENERADO y XML_FIRMADO, lista para el SRI. */
    private Factura preparada() {
        Factura emitida = new Escenario().emitir();
        xmlService.generarXml(emitida.getId());
        firmaService.firmarFactura(emitida.getId());
        return facturaRepository.findById(emitida.getId()).orElseThrow();
    }

    private FacturaDocumento documento(Factura factura, TipoDocumentoFactura tipo) {
        return facturaDocumentoRepository
                .findByFactura_IdAndTipo(factura.getId(), tipo)
                .orElseThrow(() -> new AssertionError("Falta el documento " + tipo));
    }

    private FacturaEventoSri ultimoEvento(Factura factura, OperacionSri operacion) {
        List<FacturaEventoSri> eventos = eventoRepository
                .findAllByFactura_IdAndOperacionOrderByCreadoEnDesc(factura.getId(), operacion);
        assertThat(eventos).as("eventos de %s", operacion).isNotEmpty();
        return eventos.get(0);
    }

    /** Bitacora JSONB del ultimo evento, ya parseada. */
    private JsonNode jsonDelEvento(Factura factura, OperacionSri operacion) {
        String mensajes = ultimoEvento(factura, operacion).getMensajes();
        assertThat(mensajes).isNotNull();
        try {
            return objectMapper.readTree(mensajes);
        } catch (JsonProcessingException e) {
            throw new AssertionError("La bitacora no contiene JSON valido: " + mensajes, e);
        }
    }

    /** Ambos servidores simulados agotaron sus expectativas y no recibieron mas. */
    private void verificarSri() {
        sriRecepcion.verify();
        sriAutorizacion.verify();
    }

    // ------------------------------------------------------------------
    // Respuestas del SRI simulado
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

    private static StringSource autorizado(String clave) {
        return new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <claveAccesoConsultada>%s</claveAccesoConsultada>
                    <numeroComprobantes>1</numeroComprobantes>
                    <autorizaciones><ns:autorizacion>
                      <estado>AUTORIZADO</estado>
                      <numeroAutorizacion>%s</numeroAutorizacion>
                      <fechaAutorizacion>2026-09-15T10:30:00-05:00</fechaAutorizacion>
                      <ambiente>PRUEBAS</ambiente>
                      <comprobante>&lt;factura id="comprobante" version="2.1.0"&gt;&lt;claveAcceso&gt;%s&lt;/claveAcceso&gt;&lt;/factura&gt;</comprobante>
                      <mensajes/>
                    </ns:autorizacion></autorizaciones>
                  </RespuestaAutorizacionComprobante>
                </ns:autorizacionComprobanteResponse>
                """.formatted(clave, clave, clave));
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

    private static StringSource sinAutorizaciones() {
        return new StringSource("""
                <ns:autorizacionComprobanteResponse xmlns:ns="http://ec.gob.sri.ws.autorizacion">
                  <RespuestaAutorizacionComprobante>
                    <numeroComprobantes>0</numeroComprobantes>
                    <autorizaciones/>
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
