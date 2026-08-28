package com.biopet.facturacion.sri;

import com.biopet.facturacion.entity.EstadoAutorizacionSri;

import java.time.Instant;
import java.util.List;

/**
 * Resultado de una llamada a {@code autorizacionComprobante}, ya en terminos de
 * dominio.
 *
 * @param estado             AUT, NAT o PPR. Nunca nulo: una respuesta sin
 *                           autorizaciones se normaliza a PPR (pendiente), que
 *                           es lo que significa.
 * @param numeroAutorizacion EXACTAMENTE el que devolvio el SRI. BIOPET no lo
 *                           inventa ni lo deriva de la clave de acceso, aunque
 *                           en el esquema offline ambos suelen coincidir.
 * @param fechaAutorizacion  la del SRI, o null si no vino o no se pudo
 *                           interpretar.
 * @param ambiente           texto informativo del SRI (PRODUCCION / PRUEBAS).
 * @param comprobante        XML autorizado devuelto por el SRI, tal cual. Es la
 *                           fuente de XML_AUTORIZADO.
 * @param mensajes           todos los mensajes de todas las autorizaciones.
 * @param duracionMs         cuanto tardo la llamada.
 */
public record RespuestaAutorizacionSri(EstadoAutorizacionSri estado,
                                       String numeroAutorizacion,
                                       Instant fechaAutorizacion,
                                       String ambiente,
                                       String comprobante,
                                       List<MensajeSri> mensajes,
                                       long duracionMs) {

    public RespuestaAutorizacionSri {
        mensajes = mensajes == null ? List.of() : List.copyOf(mensajes);
    }

    public boolean autorizada() {
        return estado == EstadoAutorizacionSri.AUT;
    }

    public boolean pendiente() {
        return estado == EstadoAutorizacionSri.PPR;
    }
}
