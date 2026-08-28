package com.biopet.facturacion.exception;

/**
 * El concepto de una linea no existe o fue dado de baja.
 *
 * <p>Importa que falle tambien al EMITIR, no solo al armar el borrador: entre
 * guardar el borrador y emitirlo, un administrador puede haber retirado el
 * concepto del catalogo. Emitir con el precio viejo seria congelar en un
 * comprobante fiscal algo que la clinica ya decidio que no vende.
 */
public class ConceptoFacturableNoDisponibleException extends RuntimeException {

    private final Long conceptoFacturableId;

    public ConceptoFacturableNoDisponibleException(Long conceptoFacturableId) {
        super("El concepto facturable " + conceptoFacturableId
                + " no existe o no esta activo. Revise el borrador antes de emitir.");
        this.conceptoFacturableId = conceptoFacturableId;
    }

    public Long getConceptoFacturableId() {
        return conceptoFacturableId;
    }
}
