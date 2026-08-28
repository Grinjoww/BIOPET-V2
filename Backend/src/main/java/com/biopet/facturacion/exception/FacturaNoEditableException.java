package com.biopet.facturacion.exception;

import com.biopet.facturacion.entity.EstadoFactura;

/**
 * Se intento modificar una factura que ya no esta en BORRADOR.
 *
 * <p>Una vez EMITIDA la factura consumio numeracion fiscal y quedo congelada:
 * cambiar sus lineas, su comprador o su fecha alteraria un documento que ya
 * tiene clave de acceso. Editar deja de ser una operacion valida, no una que
 * "aun no esta implementada".
 */
public class FacturaNoEditableException extends RuntimeException {

    private final Long facturaId;
    private final EstadoFactura estado;

    public FacturaNoEditableException(Long facturaId, EstadoFactura estado) {
        super("La factura " + facturaId + " esta en estado " + estado
                + " y solo puede modificarse mientras sea BORRADOR.");
        this.facturaId = facturaId;
        this.estado = estado;
    }

    public Long getFacturaId() {
        return facturaId;
    }

    public EstadoFactura getEstado() {
        return estado;
    }
}
