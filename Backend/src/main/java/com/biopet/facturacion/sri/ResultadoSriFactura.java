package com.biopet.facturacion.sri;

import com.biopet.facturacion.entity.EstadoAutorizacionSri;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.EstadoRecepcionSri;

import java.time.Instant;
import java.util.List;

/**
 * Estado de una factura frente al SRI despues de una operacion, tal y como
 * quedo persistido.
 *
 * <p>Lo devuelven {@code enviar} y {@code sincronizar} para que quien las llame
 * -hoy los tests, manana la capa REST- no tenga que volver a consultar la base
 * ni interpretar la respuesta SOAP por su cuenta.
 *
 * @param pendiente true cuando el desenlace todavia no es definitivo (PPR, o
 *        recepcion aceptada sin autorizacion resuelta). Es la senal de que hay
 *        que volver con {@code sincronizar} mas tarde.
 */
public record ResultadoSriFactura(Long facturaId,
                                  EstadoFactura estado,
                                  EstadoRecepcionSri estadoRecepcion,
                                  EstadoAutorizacionSri estadoAutorizacion,
                                  String numeroAutorizacion,
                                  Instant fechaAutorizacion,
                                  int intentosAutorizacion,
                                  Instant proximoIntentoEn,
                                  List<MensajeSri> mensajes,
                                  boolean pendiente) {

    public ResultadoSriFactura {
        mensajes = mensajes == null ? List.of() : List.copyOf(mensajes);
    }

    public boolean autorizada() {
        return estado == EstadoFactura.AUTORIZADA;
    }
}
