package com.biopet.facturacion.sri;

import com.biopet.facturacion.entity.EstadoAutorizacionSri;
import com.biopet.facturacion.sri.ws.autorizacion.AutorizacionComprobante;
import com.biopet.facturacion.sri.ws.autorizacion.AutorizacionComprobanteResponse;
import com.biopet.facturacion.sri.ws.autorizacion.AutorizacionWs;
import com.biopet.facturacion.sri.ws.autorizacion.MensajeWs;
import com.biopet.facturacion.sri.ws.autorizacion.RespuestaComprobanteWs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cliente del servicio {@code AutorizacionComprobantesOffline}.
 *
 * <p>Una sola operacion: {@code autorizacionComprobante(claveAcceso)}. Igual
 * que el de recepcion, no conoce la base de datos ni la entidad Factura, y debe
 * invocarse fuera de cualquier transaccion.
 *
 * <h2>Normalizacion del estado</h2>
 *
 * <p>La ficha tecnica nombra los estados como AUT / NAT / PPR, pero el servicio
 * los devuelve escritos (AUTORIZADO, NO AUTORIZADO, EN PROCESAMIENTO). Se
 * aceptan AMBAS formas y se rechaza cualquier otra: adivinar el desenlace
 * fiscal de una cadena desconocida seria peor que fallar. Un valor no
 * reconocido sale como fallo tecnico, la factura no cambia de estado y el caso
 * queda en la bitacora para mirarlo.
 *
 * <h2>Sin autorizaciones = PPR</h2>
 *
 * <p>Cuando el SRI todavia no ha resuelto la clave responde con
 * {@code numeroComprobantes} 0 y la lista vacia. Eso no es un error ni un
 * rechazo: es "aun en proceso", y se normaliza a PPR para que el reintento lo
 * trate como lo que es.
 */
@Component
public class SriAutorizacionClient {

    private static final Logger log = LoggerFactory.getLogger(SriAutorizacionClient.class);

    private static final String OPERACION = "autorizacion";

    /**
     * Zona del SRI. Solo se usa como ultimo recurso, si la fecha llegara sin
     * desplazamiento horario; el caso normal es que venga con offset y se
     * respete el que envio el SRI.
     */
    private static final ZoneId ZONA_SRI = ZoneId.of("America/Guayaquil");

    private final WebServiceTemplate plantilla;

    public SriAutorizacionClient(
            @Qualifier("sriAutorizacionWebServiceTemplate") WebServiceTemplate plantilla) {
        this.plantilla = plantilla;
    }

    /**
     * Consulta el estado de autorizacion de una clave de acceso.
     *
     * @param claveAcceso la clave ya congelada en la factura. Nunca una nueva.
     * @throws SriComunicacionException si no hubo respuesta funcional.
     */
    public RespuestaAutorizacionSri autorizacionComprobante(String claveAcceso) {
        if (claveAcceso == null || claveAcceso.isBlank()) {
            throw new IllegalArgumentException("No se consulta autorizacion sin clave de acceso.");
        }

        long inicio = System.nanoTime();
        Object respuesta;
        try {
            respuesta = plantilla.marshalSendAndReceive(new AutorizacionComprobante(claveAcceso));
        } catch (RuntimeException e) {
            throw TraductorFallosSri.traducir(OPERACION, transcurrido(inicio), e);
        }
        long duracionMs = transcurrido(inicio);

        if (!(respuesta instanceof AutorizacionComprobanteResponse cuerpo)
                || cuerpo.getRespuesta() == null) {
            throw new SriComunicacionException(TipoFalloSri.RESPUESTA_INVALIDA, duracionMs,
                    "El servicio de autorizacion del SRI no devolvio el elemento "
                            + "RespuestaAutorizacionComprobante.", null);
        }

        RespuestaComprobanteWs comprobante = cuerpo.getRespuesta();
        List<AutorizacionWs> autorizaciones = comprobante.lista();

        if (autorizaciones.isEmpty()) {
            log.info("SRI autorizacion: sin autorizaciones para la clave consultada; se trata "
                    + "como PPR. duracionMs={}", duracionMs);
            return new RespuestaAutorizacionSri(EstadoAutorizacionSri.PPR, null, null, null, null,
                    List.of(), duracionMs);
        }

        AutorizacionWs elegida = elegir(autorizaciones);
        EstadoAutorizacionSri estado = interpretarEstado(elegida.getEstado(), duracionMs);

        log.info("SRI autorizacion: estado={} conNumeroAutorizacion={} duracionMs={}",
                estado, elegida.getNumeroAutorizacion() != null, duracionMs);

        return new RespuestaAutorizacionSri(
                estado,
                normalizar(elegida.getNumeroAutorizacion()),
                aInstant(elegida.getFechaAutorizacion()),
                normalizar(elegida.getAmbiente()),
                elegida.getComprobante(),
                mensajes(autorizaciones),
                duracionMs);
    }

    /**
     * El SRI puede devolver varias autorizaciones para una misma clave (por
     * ejemplo un intento no autorizado y el definitivo). Se prefiere la
     * AUTORIZADA si existe; si no, la primera. Elegir la autorizada es lo
     * correcto porque una autorizacion concedida no se revoca porque hubiera
     * intentos previos fallidos, y quedarse con el primer elemento dejaria la
     * factura marcada como rechazada teniendo un AUT en la misma respuesta.
     */
    private static AutorizacionWs elegir(List<AutorizacionWs> autorizaciones) {
        for (AutorizacionWs candidata : autorizaciones) {
            if (candidata != null && esAutorizado(candidata.getEstado())) {
                return candidata;
            }
        }
        return autorizaciones.get(0);
    }

    private static boolean esAutorizado(String estado) {
        String n = normalizarEstado(estado);
        return n.equals("AUT") || n.equals("AUTORIZADO");
    }

    private static EstadoAutorizacionSri interpretarEstado(String estado, long duracionMs) {
        String n = normalizarEstado(estado);
        return switch (n) {
            case "AUT", "AUTORIZADO" -> EstadoAutorizacionSri.AUT;
            case "NAT", "NO AUTORIZADO", "NO AUTORIZADA", "RECHAZADA" -> EstadoAutorizacionSri.NAT;
            case "PPR", "EN PROCESAMIENTO", "EN PROCESO" -> EstadoAutorizacionSri.PPR;
            default -> throw new SriComunicacionException(TipoFalloSri.RESPUESTA_INVALIDA,
                    duracionMs, "El servicio de autorizacion del SRI devolvio un estado que no "
                    + "esta en el contrato: [" + estado + "].", null);
        };
    }

    private static String normalizarEstado(String estado) {
        if (estado == null) return "";
        // Colapsa espacios repetidos para que NO  AUTORIZADO tampoco se escape.
        return estado.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Todos los mensajes de todas las autorizaciones devueltas. */
    private static List<MensajeSri> mensajes(List<AutorizacionWs> autorizaciones) {
        List<MensajeSri> mensajes = new ArrayList<>();
        for (AutorizacionWs autorizacion : autorizaciones) {
            if (autorizacion == null || autorizacion.getMensajes() == null) continue;
            List<MensajeWs> lista = autorizacion.getMensajes().getMensaje();
            if (lista == null) continue;
            for (MensajeWs ws : lista) {
                if (ws == null) continue;
                mensajes.add(new MensajeSri(ws.getIdentificador(), ws.getMensaje(),
                        ws.getInformacionAdicional(), ws.getTipo()));
            }
        }
        return mensajes;
    }

    /**
     * Convierte la fecha del SRI de forma defensiva: primero con offset, y si no
     * lo trae, interpretandola en la zona del Ecuador. Si no encaja en ninguna
     * de las dos, devuelve null en lugar de romper: perder la marca temporal es
     * molesto, perder el AUT seria grave.
     */
    private static Instant aInstant(String fecha) {
        if (fecha == null || fecha.isBlank()) return null;
        String texto = fecha.trim();
        try {
            return OffsetDateTime.parse(texto).toInstant();
        } catch (DateTimeParseException ignorada) {
            // Sigue al segundo intento.
        }
        try {
            return LocalDateTime.parse(texto).atZone(ZONA_SRI).toInstant();
        } catch (DateTimeException ignorada) {
            log.warn("SRI autorizacion: fecha en formato no reconocido; se guarda sin fecha.");
            return null;
        }
    }

    private static String normalizar(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private static long transcurrido(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000L;
    }
}
