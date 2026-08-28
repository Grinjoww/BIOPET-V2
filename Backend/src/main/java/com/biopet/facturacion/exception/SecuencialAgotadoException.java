package com.biopet.facturacion.exception;

import com.biopet.facturacion.domain.AmbienteSri;

/**
 * El contador llego a 999999999 y no puede avanzar mas.
 *
 * <p>La clave de acceso reserva exactamente 9 digitos al secuencial (Ficha
 * v2.34, TABLA 1), asi que 999999999 es un tope duro, no una convencion. La
 * unica salida correcta es habilitar otro punto de emision ante el SRI, decision
 * administrativa que el servicio de reserva no puede ni debe tomar por su
 * cuenta.
 *
 * <p>Lo que NO se hace, deliberadamente: volver a 1 (reutilizaria numeracion ya
 * emitida), saltar a otro punto de emision automaticamente, ni dejar que el
 * valor desborde. El contador se queda quieto en 999999999.
 */
public class SecuencialAgotadoException extends RuntimeException {

    private final Long puntoEmisionId;
    private final AmbienteSri ambiente;

    public SecuencialAgotadoException(Long puntoEmisionId, AmbienteSri ambiente, long maximo) {
        super("El secuencial del punto de emision " + puntoEmisionId + " en el ambiente "
                + ambiente + " llego al maximo de " + maximo
                + ". Debe habilitarse un nuevo punto de emision ante el SRI.");
        this.puntoEmisionId = puntoEmisionId;
        this.ambiente = ambiente;
    }

    public Long getPuntoEmisionId() {
        return puntoEmisionId;
    }

    public AmbienteSri getAmbiente() {
        return ambiente;
    }
}
