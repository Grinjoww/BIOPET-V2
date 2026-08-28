package com.biopet.facturacion.sri;

import com.biopet.facturacion.entity.EstadoAutorizacionSri;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.EstadoRecepcionSri;

/**
 * Fotografia inmutable de todo lo que hace falta para hablar con el SRI sobre
 * una factura, leida en una transaccion corta y usada YA FUERA de ella.
 *
 * <p>Este record es la pieza que hace posible la regla dura de la fase: ninguna
 * llamada de red dentro de una transaccion de PostgreSQL. El orquestador abre
 * una transaccion, lee esto, la cierra, y solo entonces sale a la red. Como no
 * hay entidades JPA aqui -ni un proxy LAZY que pudiera intentar cargar algo-,
 * es imposible que un acceso a este objeto reabra la sesion a mitad de la
 * llamada SOAP.
 *
 * <p>{@code xmlFirmado} no se clona ni al construir ni al leer: son cientos de
 * KB y el unico consumidor es el cliente SOAP, que solo los serializa. Clonarlo
 * dos veces por envio seria pagar memoria por una garantia que aqui no aporta
 * nada.
 *
 * @param huboIntentoPrevio true si esta factura ya se envio alguna vez (hay
 *        eventos de RECEPCION en la bitacora, o consta un estado de recepcion).
 *        Es lo que dispara la regla conservadora de consultar autorizacion
 *        ANTES de reenviar nada.
 */
public record ComprobanteParaEnvio(Long facturaId,
                                   String claveAcceso,
                                   EstadoFactura estado,
                                   EstadoRecepcionSri estadoRecepcion,
                                   EstadoAutorizacionSri estadoAutorizacion,
                                   byte[] xmlFirmado,
                                   boolean huboIntentoPrevio) {
}
