package com.biopet.facturacion.sri;

import com.biopet.facturacion.entity.EstadoRecepcionSri;

import java.util.List;

/**
 * Resultado de una llamada a {@code validarComprobante}, ya en terminos de
 * dominio.
 *
 * @param estado      RECIBIDA o DEVUELTA; nunca nulo (un estado ausente o
 *                    desconocido se trata como fallo tecnico y no llega aqui).
 * @param claveAcceso la que devolvio el SRI, para poder contrastarla con la
 *                    congelada en la factura.
 * @param mensajes    TODOS los mensajes de TODOS los comprobantes de la
 *                    respuesta, en orden. Nunca se recorta a "el primero".
 * @param duracionMs  cuanto tardo la llamada, para la bitacora.
 */
public record RespuestaRecepcionSri(EstadoRecepcionSri estado,
                                    String claveAcceso,
                                    List<MensajeSri> mensajes,
                                    long duracionMs) {

    public RespuestaRecepcionSri {
        mensajes = mensajes == null ? List.of() : List.copyOf(mensajes);
    }

    public boolean recibida() {
        return estado == EstadoRecepcionSri.RECIBIDA;
    }

    /** true si alguno de los mensajes lleva el codigo indicado. */
    public boolean contieneCodigo(String codigo) {
        return mensajes.stream().anyMatch(m -> m.tieneCodigo(codigo));
    }
}
