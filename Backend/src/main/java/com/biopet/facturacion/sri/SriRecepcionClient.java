package com.biopet.facturacion.sri;

import com.biopet.facturacion.entity.EstadoRecepcionSri;
import com.biopet.facturacion.sri.ws.recepcion.MensajeWs;
import com.biopet.facturacion.sri.ws.recepcion.RespuestaSolicitudWs;
import com.biopet.facturacion.sri.ws.recepcion.ValidarComprobante;
import com.biopet.facturacion.sri.ws.recepcion.ValidarComprobanteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cliente del servicio RecepcionComprobantesOffline.
 */
@Component
public class SriRecepcionClient {

    private static final Logger log = LoggerFactory.getLogger(SriRecepcionClient.class);

    private static final String OPERACION = "recepcion";

    private final WebServiceTemplate plantilla;

    public SriRecepcionClient(
            @Qualifier("sriRecepcionWebServiceTemplate") WebServiceTemplate plantilla) {
        this.plantilla = plantilla;
    }

    public RespuestaRecepcionSri validarComprobante(byte[] xmlFirmado) {
        if (xmlFirmado == null || xmlFirmado.length == 0) {
            throw new IllegalArgumentException(
                    "No se envia un comprobante vacio al SRI.");
        }

        long inicio = System.nanoTime();
        Object respuesta;

        try {
            ValidarComprobante peticion = new ValidarComprobante(xmlFirmado);

            respuesta = plantilla.sendAndReceive(
                    mensaje -> plantilla.getMarshaller()
                            .marshal(peticion, mensaje.getPayloadResult()),

                    mensaje -> {
                        diagnosticarRespuestaReal(mensaje);

                        return plantilla.getUnmarshaller()
                                .unmarshal(mensaje.getPayloadSource());
                    });

        } catch (RuntimeException e) {
            throw TraductorFallosSri.traducir(
                    OPERACION,
                    transcurrido(inicio),
                    e);
        }

        long duracionMs = transcurrido(inicio);

        if (!(respuesta instanceof ValidarComprobanteResponse cuerpo)
                || cuerpo.getRespuesta() == null) {

            throw new SriComunicacionException(
                    TipoFalloSri.RESPUESTA_INVALIDA,
                    duracionMs,
                    "El servicio de recepcion del SRI no devolvio el elemento "
                            + "RespuestaRecepcionComprobante.",
                    null);
        }

        RespuestaSolicitudWs solicitud = cuerpo.getRespuesta();

        EstadoRecepcionSri estado =
                interpretarEstado(solicitud.getEstado(), duracionMs);

        List<MensajeSri> mensajes = mensajes(solicitud);

        log.info(
                "SRI recepcion: estado={} mensajes={} duracionMs={}",
                estado,
                mensajes.size(),
                duracionMs);

        return new RespuestaRecepcionSri(
                estado,
                claveAcceso(solicitud),
                mensajes,
                duracionMs);
    }

    /**
     * Diagnostico TEMPORAL de la respuesta REAL recibida.
     *
     * Solo registra nombres de elementos y namespaces.
     * No imprime contenido, RUC, cedula, clave de acceso,
     * nombres, XML ni datos del certificado.
     */
    private static void diagnosticarRespuestaReal(WebServiceMessage mensaje) {
        try {
            if (!(mensaje instanceof SaajSoapMessage soap)) {
                log.info(
                        "SOAP_RX_DIAG stage=RESPONSE_NOT_SAAJ type={}",
                        mensaje.getClass().getSimpleName());
                return;
            }

            log.info("SOAP_RX_DIAG stage=RESPONSE_STRUCTURE_START");

            Node body = soap.getSaajMessage().getSOAPBody();

            recorrerElementos(body, "");

            log.info("SOAP_RX_DIAG stage=RESPONSE_STRUCTURE_END");

        } catch (Exception e) {
            log.warn(
                    "SOAP_RX_DIAG stage=ERROR type={}",
                    e.getClass().getSimpleName());
        }
    }

    private static void recorrerElementos(Node padre, String ruta) {
        for (Node nodo = padre.getFirstChild();
             nodo != null;
             nodo = nodo.getNextSibling()) {

            if (nodo.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nombre = nodo.getLocalName() != null
                    ? nodo.getLocalName()
                    : nodo.getNodeName();

            String namespace = nodo.getNamespaceURI();

            if (namespace == null) {
                namespace = "";
            }

            String nuevaRuta = ruta + "/" + nombre;

            log.info(
                    "SOAP_RX_DIAG path={} namespace=[{}]",
                    nuevaRuta,
                    namespace);

            recorrerElementos(nodo, nuevaRuta);
        }
    }

    private static EstadoRecepcionSri interpretarEstado(
            String estado,
            long duracionMs) {

        String normalizado = estado == null
                ? ""
                : estado.trim().toUpperCase(Locale.ROOT);

        return switch (normalizado) {
            case "RECIBIDA" ->
                    EstadoRecepcionSri.RECIBIDA;

            case "DEVUELTA" ->
                    EstadoRecepcionSri.DEVUELTA;

            default ->
                    throw new SriComunicacionException(
                            TipoFalloSri.RESPUESTA_INVALIDA,
                            duracionMs,
                            "El servicio de recepcion del SRI devolvio un estado "
                                    + "que no esta en el contrato: ["
                                    + estado + "].",
                            null);
        };
    }

    private static List<MensajeSri> mensajes(
            RespuestaSolicitudWs solicitud) {

        List<MensajeSri> mensajes = new ArrayList<>();

        for (MensajeWs ws : solicitud.todosLosMensajes()) {
            if (ws == null) {
                continue;
            }

            mensajes.add(
                    new MensajeSri(
                            ws.getIdentificador(),
                            ws.getMensaje(),
                            ws.getInformacionAdicional(),
                            ws.getTipo()));
        }

        return mensajes;
    }

    private static String claveAcceso(
            RespuestaSolicitudWs solicitud) {

        if (solicitud.getComprobantes() == null
                || solicitud.getComprobantes().getComprobante() == null) {
            return null;
        }

        return solicitud.getComprobantes()
                .getComprobante()
                .stream()
                .filter(c ->
                        c != null
                                && c.getClaveAcceso() != null)
                .map(c -> c.getClaveAcceso().trim())
                .findFirst()
                .orElse(null);
    }

    private static long transcurrido(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000L;
    }
}