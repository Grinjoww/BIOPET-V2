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
import org.springframework.ws.client.core.WebServiceTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cliente del servicio {@code RecepcionComprobantesOffline}.
 *
 * <p>Una sola operacion: {@code validarComprobante(byte[])}. Recibe los BYTES
 * del XML firmado y los envia tal cual. Este cliente no genera XML, no firma,
 * no conoce la entidad Factura y no toca la base de datos: es una frontera pura
 * entre BIOPET y el SRI, lo que permite probarlo entero contra un servidor
 * simulado.
 *
 * <p>No abre transacciones ni participa en las que haya: quien lo llama es
 * responsable de invocarlo FUERA de una transaccion de base de datos.
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

    /**
     * Envia el comprobante firmado al SRI.
     *
     * @param xmlFirmado bytes EXACTOS del documento XML_FIRMADO persistido.
     * @throws SriComunicacionException si no hubo respuesta funcional (timeout,
     *         conexion, SOAP Fault o cuerpo que no encaja con el contrato).
     */
    public RespuestaRecepcionSri validarComprobante(byte[] xmlFirmado) {
        if (xmlFirmado == null || xmlFirmado.length == 0) {
            throw new IllegalArgumentException(
                    "No se envia un comprobante vacio al SRI.");
        }

        long inicio = System.nanoTime();
        Object respuesta;
        try {
            respuesta = plantilla.marshalSendAndReceive(new ValidarComprobante(xmlFirmado));
        } catch (RuntimeException e) {
            throw TraductorFallosSri.traducir(OPERACION, transcurrido(inicio), e);
        }
        long duracionMs = transcurrido(inicio);

        if (!(respuesta instanceof ValidarComprobanteResponse cuerpo)
                || cuerpo.getRespuesta() == null) {
            throw new SriComunicacionException(TipoFalloSri.RESPUESTA_INVALIDA, duracionMs,
                    "El servicio de recepcion del SRI no devolvio el elemento "
                            + "RespuestaRecepcionComprobante.", null);
        }

        RespuestaSolicitudWs solicitud = cuerpo.getRespuesta();
        EstadoRecepcionSri estado = interpretarEstado(solicitud.getEstado(), duracionMs);
        List<MensajeSri> mensajes = mensajes(solicitud);

        log.info("SRI recepcion: estado={} mensajes={} duracionMs={}",
                estado, mensajes.size(), duracionMs);

        return new RespuestaRecepcionSri(estado, claveAcceso(solicitud), mensajes, duracionMs);
    }

    /**
     * El contrato solo admite RECIBIDA y DEVUELTA. Cualquier otra cosa -o la
     * ausencia del campo- se trata como fallo tecnico y NO como rechazo: no se
     * puede inferir el desenlace de un valor que no esta en el contrato.
     */
    private static EstadoRecepcionSri interpretarEstado(String estado, long duracionMs) {
        String normalizado = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        return switch (normalizado) {
            case "RECIBIDA" -> EstadoRecepcionSri.RECIBIDA;
            case "DEVUELTA" -> EstadoRecepcionSri.DEVUELTA;
            default -> throw new SriComunicacionException(TipoFalloSri.RESPUESTA_INVALIDA,
                    duracionMs, "El servicio de recepcion del SRI devolvio un estado que no esta "
                    + "en el contrato: [" + estado + "].", null);
        };
    }

    /** TODOS los mensajes de TODOS los comprobantes, sin descartar ninguno. */
    private static List<MensajeSri> mensajes(RespuestaSolicitudWs solicitud) {
        List<MensajeSri> mensajes = new ArrayList<>();
        for (MensajeWs ws : solicitud.todosLosMensajes()) {
            if (ws == null) continue;
            mensajes.add(new MensajeSri(ws.getIdentificador(), ws.getMensaje(),
                    ws.getInformacionAdicional(), ws.getTipo()));
        }
        return mensajes;
    }

    private static String claveAcceso(RespuestaSolicitudWs solicitud) {
        if (solicitud.getComprobantes() == null
                || solicitud.getComprobantes().getComprobante() == null) {
            return null;
        }
        return solicitud.getComprobantes().getComprobante().stream()
                .filter(c -> c != null && c.getClaveAcceso() != null)
                .map(c -> c.getClaveAcceso().trim())
                .findFirst()
                .orElse(null);
    }

    private static long transcurrido(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000L;
    }
}
