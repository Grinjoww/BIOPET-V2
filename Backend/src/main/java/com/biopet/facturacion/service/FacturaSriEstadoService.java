package com.biopet.facturacion.service;

import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.entity.EstadoAutorizacionSri;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.EstadoRecepcionSri;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.FacturaEventoSri;
import com.biopet.facturacion.entity.OperacionSri;
import com.biopet.facturacion.entity.ResultadoEventoSri;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.exception.AutorizacionSriInconsistenteException;
import com.biopet.facturacion.exception.FacturaNoEnviableException;
import com.biopet.facturacion.exception.FacturaXmlInvalidoException;
import com.biopet.facturacion.repository.FacturaDocumentoRepository;
import com.biopet.facturacion.repository.FacturaEventoSriRepository;
import com.biopet.facturacion.repository.FacturaRepository;
import com.biopet.facturacion.sri.ComprobanteParaEnvio;
import com.biopet.facturacion.sri.MensajeSri;
import com.biopet.facturacion.sri.RespuestaAutorizacionSri;
import com.biopet.facturacion.sri.RespuestaRecepcionSri;
import com.biopet.facturacion.sri.ResultadoSriFactura;
import com.biopet.facturacion.sri.SriComunicacionException;
import com.biopet.facturacion.sri.TipoFalloSri;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Las transacciones CORTAS del dialogo con el SRI: leer lo necesario antes de
 * salir a la red, y persistir el resultado cuando se vuelve.
 *
 * <h2>Por que este servicio existe aparte del orquestador</h2>
 *
 * <p>Es la pieza que hace cumplible, y sobre todo AUDITABLE, la regla dura de
 * la fase: <b>ninguna llamada de red dentro de una transaccion de
 * PostgreSQL</b>. Todos los metodos {@code @Transactional} del pipeline SRI
 * viven aqui y ninguno de ellos toca un cliente SOAP; el orquestador
 * ({@link FacturaSriService}) es el que llama al SRI y no lleva
 * {@code @Transactional} en ningun sitio. La separacion se puede comprobar de
 * un vistazo -o con un grep- en lugar de tener que leer un metodo largo para
 * convencerse de que la red queda fuera.
 *
 * <p>El motivo de fondo no es estetico. Una conexion de PostgreSQL retenida
 * mientras se espera 60 s a un servicio externo es una conexion del pool que
 * nadie mas puede usar, con transaccion abierta, bloqueos vivos y VACUUM
 * frenado. Con el plan gratuito de Render, donde el pool es de unas pocas
 * conexiones, bastan tres facturas simultaneas contra un SRI lento para dejar
 * sin base de datos a toda la clinica.
 *
 * <p>Cada metodo publico es una unidad transaccional independiente
 * ({@code REQUIRES_NEW}): aunque en el futuro alguien envolviera una llamada al
 * orquestador en una transaccion propia, estas seguirian abriendo y cerrando la
 * suya, y la de fuera no se quedaria colgando durante la llamada SOAP.
 */
@Service
public class FacturaSriEstadoService {

    private static final Logger log = LoggerFactory.getLogger(FacturaSriEstadoService.class);

    /** Primera espera antes de volver a consultar una autorizacion pendiente. */
    static final Duration ESPERA_BASE = Duration.ofMinutes(1);

    /** Techo de la espera: mas alla de esto no se gana nada esperando. */
    static final Duration ESPERA_MAXIMA = Duration.ofMinutes(30);

    /** Longitud de la columna {@code facturas.numero_autorizacion}. */
    private static final int MAX_NUMERO_AUTORIZACION = 49;

    private final FacturaRepository facturaRepository;
    private final FacturaDocumentoRepository facturaDocumentoRepository;
    private final FacturaEventoSriRepository facturaEventoSriRepository;
    private final ObjectMapper objectMapper;

    public FacturaSriEstadoService(FacturaRepository facturaRepository,
                                   FacturaDocumentoRepository facturaDocumentoRepository,
                                   FacturaEventoSriRepository facturaEventoSriRepository,
                                   ObjectMapper objectMapper) {
        this.facturaRepository = facturaRepository;
        this.facturaDocumentoRepository = facturaDocumentoRepository;
        this.facturaEventoSriRepository = facturaEventoSriRepository;
        this.objectMapper = objectMapper;
    }

    // ==================================================================
    // Lecturas previas (transaccion corta, de solo lectura)
    // ==================================================================

    /**
     * Reune todo lo que hace falta para enviar el comprobante y cierra la
     * transaccion antes de que nadie toque la red.
     *
     * <p>El XML firmado se lee y se COMPRUEBA aqui (SHA-256 contra el contenido
     * real). Es el punto correcto: si los bytes de la base estan corruptos no se
     * debe abrir siquiera la conexion con el SRI. Solo se carga cuando la
     * factura sigue EMITIDA; si ya esta autorizada o rechazada no hay nada que
     * enviar y traer cientos de KB desde la base seria trabajo tirado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ComprobanteParaEnvio prepararEnvio(Long facturaId) {
        Factura factura = exigirFactura(facturaId);

        if (factura.getEstado() == EstadoFactura.BORRADOR) {
            throw new FacturaNoEnviableException(
                    "La factura " + facturaId + " es un BORRADOR: no tiene clave de acceso ni "
                            + "secuencial, no existe fiscalmente y no se envia al SRI.");
        }

        byte[] xmlFirmado = null;
        if (factura.getEstado() == EstadoFactura.EMITIDA) {
            exigirClaveAcceso(factura);
            xmlFirmado = leerXmlFirmado(facturaId);
        }

        return new ComprobanteParaEnvio(
                factura.getId(),
                factura.getClaveAcceso(),
                factura.getEstado(),
                factura.getEstadoRecepcion(),
                factura.getEstadoAutorizacion(),
                xmlFirmado,
                huboIntentoPrevio(factura));
    }

    /**
     * Lo minimo para consultar autorizacion: la clave de acceso y el estado. No
     * se lee el XML firmado porque la consulta no lo necesita, y no leerlo hace
     * que sincronizar sea barato incluso con muchas facturas pendientes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ComprobanteParaEnvio prepararSincronizacion(Long facturaId) {
        Factura factura = exigirFactura(facturaId);

        if (factura.getEstado() == EstadoFactura.BORRADOR) {
            throw new FacturaNoEnviableException(
                    "La factura " + facturaId + " es un BORRADOR: no hay nada que sincronizar "
                            + "con el SRI.");
        }
        exigirClaveAcceso(factura);

        return new ComprobanteParaEnvio(
                factura.getId(),
                factura.getClaveAcceso(),
                factura.getEstado(),
                factura.getEstadoRecepcion(),
                factura.getEstadoAutorizacion(),
                null,
                huboIntentoPrevio(factura));
    }

    /** Estado actual persistido, sin tocar el SRI. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ResultadoSriFactura estadoActual(Long facturaId) {
        Factura factura = exigirFactura(facturaId);
        return resultado(factura, List.of(), esPendiente(factura));
    }

    // ==================================================================
    // Escrituras (transaccion corta, ya de vuelta de la red)
    // ==================================================================

    /**
     * Persiste el desenlace de una llamada de RECEPCION.
     *
     * @param rechazoDefinitivo si la devolucion es un rechazo real del
     *        comprobante. Se decide fuera, en el orquestador, porque depende de
     *        los CODIGOS de los mensajes: una devolucion por "clave ya
     *        registrada" o por "en procesamiento" no es un rechazo, y marcar
     *        RECHAZADA en esos casos seria escribir un estado falso sobre una
     *        factura que el SRI puede estar a punto de autorizar.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoSriFactura registrarRecepcion(Long facturaId, RespuestaRecepcionSri respuesta,
                                                  boolean rechazoDefinitivo) {
        Factura factura = bloquear(facturaId);

        ResultadoEventoSri resultadoEvento = respuesta.recibida()
                ? ResultadoEventoSri.RECIBIDA
                : ResultadoEventoSri.DEVUELTA;
        registrarEvento(factura, OperacionSri.RECEPCION, resultadoEvento,
                respuesta.mensajes(), respuesta.duracionMs());

        avisarSiLaClaveNoCoincide(factura, respuesta.claveAcceso());

        factura.setEstadoRecepcion(respuesta.estado());
        if (rechazoDefinitivo) {
            factura.setEstado(EstadoFactura.RECHAZADA);
            factura.setProximoIntentoEn(null);
        } else {
            factura.setProximoIntentoEn(Instant.now().plus(ESPERA_BASE));
        }
        facturaRepository.saveAndFlush(factura);

        return resultado(factura, respuesta.mensajes(), !rechazoDefinitivo);
    }

    /**
     * Persiste el desenlace de una consulta de AUTORIZACION.
     *
     * <p>Es el UNICO metodo de todo el modulo que puede poner una factura en
     * AUTORIZADA, y solo lo hace ante un AUT. Ni un HTTP 200, ni una recepcion
     * aceptada, ni la ausencia de errores bastan.
     *
     * <p>Una factura ya AUTORIZADA no se degrada nunca: la consulta se registra
     * en la bitacora y el estado se deja como esta -no se reescriben campos ni
     * se cuenta un intento nuevo-. Una autorizacion concedida es un hecho
     * fiscal, no una lectura que caduque.
     *
     * <p>Eso si: si esa consulta trae un nuevo AUT, SI se contrasta contra el
     * {@code XML_AUTORIZADO} ya archivado (ver {@link #persistirXmlAutorizado}).
     * No para cambiar nada -la factura no se toca-, sino para no dejar pasar en
     * silencio una respuesta del SRI que contradiga lo que ya se archivo para
     * esa misma clave: eso es un fallo, y debe sonar como tal.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoSriFactura registrarAutorizacion(Long facturaId,
                                                     RespuestaAutorizacionSri respuesta) {
        Factura factura = bloquear(facturaId);

        registrarEvento(factura, OperacionSri.AUTORIZACION, evento(respuesta.estado()),
                respuesta.mensajes(), respuesta.duracionMs());

        if (factura.getEstado() == EstadoFactura.AUTORIZADA) {
            if (respuesta.estado() == EstadoAutorizacionSri.AUT) {
                // No cambia nada de la factura -ni estado, ni numero, ni
                // intentos-, pero SI verifica que el AUT que acaba de llegar no
                // contradiga el que ya se archivo.
                persistirXmlAutorizado(factura, respuesta);
            } else {
                log.info("Factura {}: ya estaba AUTORIZADA; una respuesta {} posterior no cambia "
                        + "nada, solo queda en la bitacora.", facturaId, respuesta.estado());
            }
            return resultado(factura, respuesta.mensajes(), false);
        }

        factura.setIntentosAutorizacion(factura.getIntentosAutorizacion() + 1);

        boolean pendiente = false;
        switch (respuesta.estado()) {
            case AUT -> {
                aplicarAutorizacion(factura, respuesta);
                factura.setProximoIntentoEn(null);
            }
            case NAT -> {
                factura.setEstado(EstadoFactura.RECHAZADA);
                factura.setEstadoAutorizacion(EstadoAutorizacionSri.NAT);
                factura.setProximoIntentoEn(null);
            }
            case PPR -> {
                // Ni AUTORIZADA ni RECHAZADA: sigue EMITIDA y se volvera a
                // preguntar. El estado de autorizacion si se guarda, para que
                // se vea que el SRI la tiene en proceso.
                factura.setEstadoAutorizacion(EstadoAutorizacionSri.PPR);
                factura.setProximoIntentoEn(
                        Instant.now().plus(espera(factura.getIntentosAutorizacion())));
                pendiente = true;
            }
        }

        facturaRepository.saveAndFlush(factura);
        return resultado(factura, respuesta.mensajes(), pendiente);
    }

    /**
     * Persiste un fallo tecnico (timeout, conexion, SOAP Fault, respuesta
     * ininteligible).
     *
     * <p>Lo importante es lo que NO hace: no cambia el estado de la factura, no
     * la marca RECHAZADA, no toca la clave, el secuencial ni los documentos.
     * Solo deja constancia de que se intento y de como fallo, y fija cuando
     * volver a intentarlo. La factura queda exactamente como estaba, lista para
     * reintentar con la MISMA clave y el MISMO XML firmado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoSriFactura registrarFallo(Long facturaId, OperacionSri operacion,
                                              SriComunicacionException fallo) {
        Factura factura = bloquear(facturaId);

        registrarEvento(factura, operacion, fallo.getTipo().resultadoEvento(),
                mensajesDeFallo(fallo), fallo.getDuracionMs());

        if (operacion == OperacionSri.AUTORIZACION) {
            factura.setIntentosAutorizacion(factura.getIntentosAutorizacion() + 1);
        }
        factura.setProximoIntentoEn(
                Instant.now().plus(espera(Math.max(1, factura.getIntentosAutorizacion()))));
        facturaRepository.saveAndFlush(factura);

        return resultado(factura, List.of(), true);
    }

    // ==================================================================
    // Detalle
    // ==================================================================

    /**
     * Aplica un AUT: estado, codigo, numero y fecha del SRI, y el comprobante
     * autorizado como documento propio.
     *
     * <p>El numero de autorizacion es EXACTAMENTE el que devolvio el servicio.
     * BIOPET no lo genera ni lo deriva de la clave de acceso: en el esquema
     * offline suelen coincidir, y precisamente por eso se comprueba y se deja
     * dicho en el log en lugar de darlo por supuesto.
     */
    private void aplicarAutorizacion(Factura factura, RespuestaAutorizacionSri respuesta) {
        factura.setEstado(EstadoFactura.AUTORIZADA);
        factura.setEstadoAutorizacion(EstadoAutorizacionSri.AUT);
        factura.setNumeroAutorizacion(numeroAutorizacion(factura, respuesta.numeroAutorizacion()));
        factura.setFechaAutorizacion(respuesta.fechaAutorizacion());

        if (respuesta.fechaAutorizacion() == null) {
            log.warn("Factura {}: el SRI autorizo sin fecha de autorizacion legible. Se guarda "
                    + "sin fecha antes que inventar una.", factura.getId());
        }
        // Un AUT demuestra que el SRI recibio el comprobante, aunque BIOPET no
        // llegara a ver la respuesta de recepcion (tipico tras un timeout). Solo
        // se rellena si estaba vacio: si el SRI dijo DEVUELTA en su momento, esa
        // devolucion ocurrio y no se reescribe.
        if (factura.getEstadoRecepcion() == null) {
            factura.setEstadoRecepcion(EstadoRecepcionSri.RECIBIDA);
        }

        persistirXmlAutorizado(factura, respuesta);
    }

    /**
     * Guarda como {@link TipoDocumentoFactura#XML_AUTORIZADO} la UNIDAD DE
     * AUTORIZACION completa que devolvio el SRI, no solo el comprobante que va
     * dentro.
     *
     * <p>La respuesta oficial de {@code autorizacionComprobante} es:
     *
     * <pre>
     *   &lt;autorizacion&gt;
     *     &lt;estado&gt;AUTORIZADO&lt;/estado&gt;
     *     &lt;numeroAutorizacion&gt;...&lt;/numeroAutorizacion&gt;
     *     &lt;fechaAutorizacion&gt;...&lt;/fechaAutorizacion&gt;
     *     &lt;ambiente&gt;...&lt;/ambiente&gt;
     *     &lt;comprobante&gt;&lt;![CDATA[ ...XML firmado... ]]&gt;&lt;/comprobante&gt;
     *     &lt;mensajes&gt;...&lt;/mensajes&gt;
     *   &lt;/autorizacion&gt;
     * </pre>
     *
     * <p>Archivar solo el {@code &lt;comprobante&gt;} interno perderia el numero
     * y la fecha de autorizacion TAL COMO los emitio el SRI para ESTE documento
     * -{@code facturas.numero_autorizacion} puede quedar vacio si no cupiera en
     * la columna, ver {@link #numeroAutorizacion}- y los mensajes que hubiera
     * (p. ej. una advertencia junto a un AUT). Por eso se reconstruye la unidad
     * entera con las APIs de DOM/Transformer, nunca concatenando texto: el
     * comprobante que trae el SRI es contenido externo y debe entrar en el
     * documento por el mecanismo de escape del propio XML, no por interpolacion
     * de Strings.
     *
     * <p>Se guarda APARTE. XML_GENERADO y XML_FIRMADO no se tocan: los tres
     * coexisten, cada uno con su SHA-256. No se comprueba que el
     * {@code <comprobante>} interno coincida con los bytes del XML firmado,
     * precisamente porque no tiene por que hacerlo: el SRI puede reserializarlo.
     * Lo que se archiva es lo que el SRI dice haber autorizado, que es la
     * version que cuenta ante la administracion.
     *
     * <h2>Idempotencia sin sobrescritura silenciosa</h2>
     *
     * <p>Si ya hay un XML_AUTORIZADO se compara con la respuesta actual. Bytes
     * identicos: no-op. Los campos MATERIALES (estado, numero, fecha,
     * comprobante) coinciden aunque los bytes difieran -p. ej. cambio en los
     * mensajes de una consulta a otra-: se conserva el documento ya archivado, y
     * no se reemplaza; el archivo de la autorizacion no debe cambiar despues de
     * escrito. Si los campos materiales NO coinciden, es una respuesta del SRI
     * que contradice lo ya archivado para la misma clave: eso no se resuelve en
     * silencio, se declara {@link AutorizacionSriInconsistenteException}.
     */
    private void persistirXmlAutorizado(Factura factura, RespuestaAutorizacionSri respuesta) {
        if (respuesta.comprobante() == null || respuesta.comprobante().isBlank()) {
            log.warn("Factura {}: el SRI autorizo pero no devolvio el comprobante en la "
                    + "respuesta. No se guarda XML_AUTORIZADO.", factura.getId());
            return;
        }

        byte[] candidato = construirXmlAutorizado(respuesta);
        String hashCandidato = FacturaXmlService.sha256(candidato);

        Optional<FacturaDocumento> existente = facturaDocumentoRepository
                .findByFactura_IdAndTipo(factura.getId(), TipoDocumentoFactura.XML_AUTORIZADO);
        if (existente.isPresent()) {
            FacturaDocumento documento = existente.get();
            String real = FacturaXmlService.sha256(documento.getContenido());
            if (!real.equals(documento.getSha256())) {
                throw new FacturaXmlInvalidoException(
                        "El XML autorizado guardado de la factura " + factura.getId()
                                + " no corresponde a su SHA-256. El contenido esta corrupto.");
            }
            if (real.equals(hashCandidato)) {
                return;
            }

            CamposMaterialesAutorizacion previos = leerCamposMateriales(documento.getContenido());
            CamposMaterialesAutorizacion nuevos = CamposMaterialesAutorizacion.de(respuesta);
            if (!previos.equals(nuevos)) {
                throw new AutorizacionSriInconsistenteException(
                        "La factura " + factura.getId() + " ya tiene un XML_AUTORIZADO archivado "
                                + "(" + previos + ") y el SRI acaba de devolver una autorizacion "
                                + "distinta para la MISMA clave (" + nuevos + "). No se sobrescribe "
                                + "en silencio: revisar manualmente antes de continuar.");
            }
            // Mismos datos materiales, distinto detalle accesorio (p. ej. los
            // mensajes de esta consulta): se conserva el documento archivado.
            log.info("Factura {}: nueva consulta de autorizacion con los mismos datos "
                    + "materiales; se conserva el XML_AUTORIZADO ya archivado.", factura.getId());
            return;
        }

        facturaDocumentoRepository.saveAndFlush(FacturaDocumento.builder()
                .factura(factura)
                .tipo(TipoDocumentoFactura.XML_AUTORIZADO)
                .contenido(candidato)
                .sha256(hashCandidato)
                .bytes(candidato.length)
                .build());
    }

    /**
     * Construye el documento {@code <autorizacion>} completo a partir de la
     * respuesta del SRI, en UTF-8, usando DOM + {@link Transformer}: cada valor
     * se coloca con {@code setTextContent}/{@code CDATASection}, que escapan por
     * si mismos. Nada de esto se arma concatenando Strings.
     */
    private static byte[] construirXmlAutorizado(RespuestaAutorizacionSri respuesta) {
        Document documento = nuevoDocumento();
        Element raiz = documento.createElement("autorizacion");
        documento.appendChild(raiz);

        agregarTexto(documento, raiz, "estado", respuesta.estado().name());
        agregarTexto(documento, raiz, "numeroAutorizacion", respuesta.numeroAutorizacion());
        agregarTexto(documento, raiz, "fechaAutorizacion",
                respuesta.fechaAutorizacion() == null ? null : respuesta.fechaAutorizacion().toString());
        agregarTexto(documento, raiz, "ambiente", respuesta.ambiente());

        Element comprobante = documento.createElement("comprobante");
        agregarCdataSegura(documento, comprobante, respuesta.comprobante());
        raiz.appendChild(comprobante);

        if (!respuesta.mensajes().isEmpty()) {
            Element mensajes = documento.createElement("mensajes");
            for (MensajeSri mensaje : respuesta.mensajes()) {
                Element nodo = documento.createElement("mensaje");
                agregarTexto(documento, nodo, "identificador", mensaje.identificador());
                agregarTexto(documento, nodo, "mensaje", mensaje.mensaje());
                agregarTexto(documento, nodo, "informacionAdicional", mensaje.informacionAdicional());
                agregarTexto(documento, nodo, "tipo", mensaje.tipo());
                mensajes.appendChild(nodo);
            }
            raiz.appendChild(mensajes);
        }

        return serializar(documento);
    }

    /** Solo anade el elemento si hay valor: no se archivan etiquetas vacias. */
    private static void agregarTexto(Document documento, Element padre, String nombre, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        Element hijo = documento.createElement(nombre);
        hijo.setTextContent(valor);
        padre.appendChild(hijo);
    }

    /**
     * Anade el contenido como una o varias {@link CDATASection}, partiendo el
     * texto en cada aparicion de {@code ]]>} -la unica secuencia que no puede
     * aparecer DENTRO de una seccion CDATA-. Con el comprobante del SRI, que es
     * el propio XML firmado, esta secuencia no deberia darse nunca, pero
     * partirla en lugar de asumirlo es la unica forma de que esto siga siendo
     * XML valido pase lo que pase en el contenido.
     */
    private static void agregarCdataSegura(Document documento, Element padre, String contenido) {
        String restante = contenido;
        int corte;
        while ((corte = restante.indexOf("]]>")) != -1) {
            padre.appendChild(documento.createCDATASection(restante.substring(0, corte + 2)));
            restante = restante.substring(corte + 2);
        }
        padre.appendChild(documento.createCDATASection(restante));
    }

    private static Document nuevoDocumento() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().newDocument();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear el documento XML_AUTORIZADO.", e);
        }
    }

    private static byte[] serializar(Document documento) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(documento), new StreamResult(salida));
            return salida.toByteArray();
        } catch (TransformerException e) {
            throw new IllegalStateException("No se pudo serializar el XML_AUTORIZADO.", e);
        }
    }

    /**
     * Los campos que hacen que dos autorizaciones de la MISMA clave sean "la
     * misma autorizacion" o no. Se usan para decidir si una segunda consulta
     * puede convivir con el documento ya archivado o si hay que declarar
     * inconsistencia; los mensajes NO son materiales -pueden variar de una
     * consulta a otra sin que cambie el hecho fiscal- y por eso quedan fuera.
     */
    private record CamposMaterialesAutorizacion(String estado, String numeroAutorizacion,
                                                 String fechaAutorizacion, String comprobante) {
        static CamposMaterialesAutorizacion de(RespuestaAutorizacionSri respuesta) {
            return new CamposMaterialesAutorizacion(
                    respuesta.estado() == null ? null : respuesta.estado().name(),
                    normalizar(respuesta.numeroAutorizacion()),
                    respuesta.fechaAutorizacion() == null ? null : respuesta.fechaAutorizacion().toString(),
                    normalizar(respuesta.comprobante()));
        }

        static CamposMaterialesAutorizacion de(String estado, String numeroAutorizacion,
                                               String fechaAutorizacion, String comprobante) {
            return new CamposMaterialesAutorizacion(normalizar(estado), normalizar(numeroAutorizacion),
                    normalizar(fechaAutorizacion), normalizar(comprobante));
        }

        private static String normalizar(String valor) {
            return valor == null || valor.isBlank() ? null : valor.trim();
        }

        @Override
        public String toString() {
            return "estado=" + estado + ", numeroAutorizacion=" + numeroAutorizacion
                    + ", fechaAutorizacion=" + fechaAutorizacion;
        }
    }

    /**
     * Relee un XML_AUTORIZADO ya persistido y extrae sus campos materiales, para
     * poder comparar una respuesta nueva contra lo archivado sin volver a
     * confiar en los bytes crudos. Parser endurecido -sin DOCTYPE- aunque el
     * origen sea nuestra propia base: es la misma disciplina que el resto del
     * modulo aplica a cualquier XML que se vuelve a parsear.
     */
    private static CamposMaterialesAutorizacion leerCamposMateriales(byte[] xmlAutorizado) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document documento = builder.parse(new ByteArrayInputStream(xmlAutorizado));

            return CamposMaterialesAutorizacion.de(
                    textoDe(documento, "estado"),
                    textoDe(documento, "numeroAutorizacion"),
                    textoDe(documento, "fechaAutorizacion"),
                    textoDe(documento, "comprobante"));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new FacturaXmlInvalidoException(
                    "El XML_AUTORIZADO archivado no se pudo releer para comparar: " + e.getMessage(), e);
        }
    }

    private static String textoDe(Document documento, String etiqueta) {
        NodeList nodos = documento.getElementsByTagName(etiqueta);
        if (nodos.getLength() == 0) {
            return null;
        }
        Node nodo = nodos.item(0);
        return nodo.getTextContent();
    }

    /**
     * Devuelve el numero de autorizacion que quepa en la columna, avisando si no
     * cabe o si no coincide con la clave de acceso.
     *
     * <p>En el esquema offline el SRI devuelve como numero de autorizacion la
     * propia clave de acceso de 49 digitos. Si algun dia devolviera algo mas
     * largo, truncarlo seria peor que no guardarlo: quedaria un dato que parece
     * un numero de autorizacion y no lo es. Se deja la columna vacia y el valor
     * completo queda en la bitacora del evento, que si lo admite.
     */
    private String numeroAutorizacion(Factura factura, String numero) {
        if (numero == null) {
            log.warn("Factura {}: autorizacion AUT sin numero de autorizacion en la respuesta.",
                    factura.getId());
            return null;
        }
        if (numero.length() > MAX_NUMERO_AUTORIZACION) {
            log.error("Factura {}: el SRI devolvio un numero de autorizacion de {} caracteres, "
                            + "mas de los {} que admite la columna. No se guarda truncado; queda "
                            + "en la bitacora del evento.",
                    factura.getId(), numero.length(), MAX_NUMERO_AUTORIZACION);
            return null;
        }
        if (factura.getClaveAcceso() != null && !numero.equals(factura.getClaveAcceso())) {
            log.info("Factura {}: el numero de autorizacion devuelto por el SRI NO coincide con "
                    + "la clave de acceso. Se guarda el del SRI, que es la fuente de verdad.",
                    factura.getId());
        }
        return numero;
    }

    /**
     * Anade una fila a la bitacora. Append-only: nunca se actualiza ni se borra.
     *
     * <p>{@code intento} se numera contando los eventos previos de ESA operacion,
     * empezando en 1 como exige {@code chk_factura_eventos_sri_intento}.
     */
    private void registrarEvento(Factura factura, OperacionSri operacion,
                                 ResultadoEventoSri resultado, List<MensajeSri> mensajes,
                                 long duracionMs) {
        long previos = facturaEventoSriRepository
                .countByFactura_IdAndOperacion(factura.getId(), operacion);

        facturaEventoSriRepository.saveAndFlush(FacturaEventoSri.builder()
                .factura(factura)
                .operacion(operacion)
                .resultado(resultado)
                .mensajes(aJson(mensajes))
                .duracionMs(Math.max(0L, duracionMs))
                .intento((int) previos + 1)
                .build());
    }

    /**
     * Serializa los mensajes funcionales del SRI a JSONB.
     *
     * <p>Se guardan los mensajes, NO el sobre SOAP. El envelope completo pesa,
     * repite el comprobante entero en cada intento y no aporta nada que no este
     * ya en {@code factura_documentos}; los mensajes son justo la parte que hace
     * falta para explicar un rechazo. Aqui no entra ningun secreto: son textos
     * publicos del catalogo del SRI.
     */
    private String aJson(List<MensajeSri> mensajes) {
        if (mensajes == null || mensajes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(mensajes);
        } catch (JsonProcessingException e) {
            // Que la bitacora no pueda serializarse no debe tumbar una
            // autorizacion valida: se degrada a un JSON minimo y se avisa.
            log.error("No se pudieron serializar los mensajes del SRI a JSON.", e);
            return "[{\"identificador\":\"BIOPET\",\"mensaje\":\"mensajes no serializables\"}]";
        }
    }

    /**
     * Un fallo tecnico tambien deja rastro util. En un TIMEOUT no hubo cuerpo
     * que guardar y la columna queda nula, que es lo que significa; en un SOAP
     * Fault o un error de conexion se guarda el diagnostico como un mensaje mas,
     * con el tipo de fallo como identificador.
     */
    private static List<MensajeSri> mensajesDeFallo(SriComunicacionException fallo) {
        if (fallo.getTipo() == TipoFalloSri.TIMEOUT) {
            return List.of();
        }
        List<MensajeSri> mensajes = new ArrayList<>();
        mensajes.add(new MensajeSri(fallo.getTipo().name(), fallo.getMessage(), null, "ERROR"));
        return mensajes;
    }

    /**
     * Espera antes del siguiente intento: 1, 2, 4... minutos, con techo de 30.
     *
     * <p>Crece para no castigar a un SRI que ya va lento, y tiene techo para que
     * una factura pendiente no acabe consultandose una vez al dia. No hay ningun
     * {@code sleep} en el camino de la peticion: esto solo fija la marca
     * {@code proximo_intento_en} que leera quien reintente.
     */
    static Duration espera(int intentos) {
        int exponente = Math.min(Math.max(intentos, 1) - 1, 10);
        Duration espera = ESPERA_BASE.multipliedBy(1L << exponente);
        return espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera;
    }

    private static ResultadoEventoSri evento(EstadoAutorizacionSri estado) {
        return switch (estado) {
            case AUT -> ResultadoEventoSri.AUT;
            case NAT -> ResultadoEventoSri.NAT;
            case PPR -> ResultadoEventoSri.PPR;
        };
    }

    private static ResultadoSriFactura resultado(Factura factura, List<MensajeSri> mensajes,
                                                 boolean pendiente) {
        return new ResultadoSriFactura(
                factura.getId(),
                factura.getEstado(),
                factura.getEstadoRecepcion(),
                factura.getEstadoAutorizacion(),
                factura.getNumeroAutorizacion(),
                factura.getFechaAutorizacion(),
                factura.getIntentosAutorizacion() == null ? 0 : factura.getIntentosAutorizacion(),
                factura.getProximoIntentoEn(),
                mensajes,
                pendiente);
    }

    /** Pendiente = ni autorizada ni rechazada definitivamente. */
    private static boolean esPendiente(Factura factura) {
        return factura.getEstado() == EstadoFactura.EMITIDA;
    }

    private boolean huboIntentoPrevio(Factura factura) {
        if (factura.getEstadoRecepcion() != null) {
            return true;
        }
        return facturaEventoSriRepository
                .countByFactura_IdAndOperacion(factura.getId(), OperacionSri.RECEPCION) > 0;
    }

    private byte[] leerXmlFirmado(Long facturaId) {
        FacturaDocumento firmado = facturaDocumentoRepository
                .findByFactura_IdAndTipo(facturaId, TipoDocumentoFactura.XML_FIRMADO)
                .orElseThrow(() -> new FacturaNoEnviableException(
                        "La factura " + facturaId + " no tiene XML firmado: no hay comprobante "
                                + "que enviar al SRI."));

        String real = FacturaXmlService.sha256(firmado.getContenido());
        if (!real.equals(firmado.getSha256())) {
            throw new FacturaXmlInvalidoException(
                    "El XML firmado de la factura " + facturaId + " no corresponde a su SHA-256 "
                            + "(esperado " + firmado.getSha256() + ", calculado " + real
                            + "). No se envia al SRI un comprobante corrupto.");
        }
        return firmado.getContenido();
    }

    private void avisarSiLaClaveNoCoincide(Factura factura, String claveDelSri) {
        if (claveDelSri != null && !claveDelSri.equals(factura.getClaveAcceso())) {
            log.warn("Factura {}: el SRI respondio sobre una clave de acceso distinta de la "
                    + "congelada en la factura. Se conserva la de la factura.", factura.getId());
        }
    }

    private static void exigirClaveAcceso(Factura factura) {
        if (factura.getClaveAcceso() == null || factura.getClaveAcceso().isBlank()) {
            throw new FacturaNoEnviableException(
                    "La factura " + factura.getId() + " no tiene clave de acceso. Nunca se genera "
                            + "una nueva para poder enviarla: eso duplicaria el comprobante.");
        }
    }

    private Factura exigirFactura(Long facturaId) {
        if (facturaId == null) {
            throw new IllegalArgumentException("El identificador de la factura es obligatorio.");
        }
        return facturaRepository.findById(facturaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la factura con id " + facturaId + "."));
    }

    private Factura bloquear(Long facturaId) {
        return facturaRepository.bloquearParaSincronizarConSri(facturaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la factura con id " + facturaId + "."));
    }
}
