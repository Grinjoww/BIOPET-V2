package com.biopet.facturacion.exception;

import com.biopet.facturacion.domain.CodigoImpuestoSri;

import java.time.LocalDate;

/**
 * Dos o mas tarifas activas cubren la misma fecha para el mismo par (codigo de
 * impuesto, codigo de porcentaje).
 *
 * <p>Es configuracion incorrecta: los periodos de vigencia deberian ser
 * disjuntos. Se falla a proposito en lugar de quedarse con la primera, porque
 * elegir en silencio significaria emitir comprobantes con una tarifa decidida
 * por el orden de un ORDER BY. Un error de configuracion tributaria debe verse,
 * no absorberse.
 */
public class TarifaImpuestoAmbiguaException extends RuntimeException {

    public TarifaImpuestoAmbiguaException(CodigoImpuestoSri codigoImpuesto,
                                          String codigoPorcentaje,
                                          LocalDate fecha,
                                          int encontradas) {
        super("Hay " + encontradas + " tarifas activas que cubren la fecha " + fecha
                + " para el impuesto " + codigoImpuesto.codigo() + " con codigo de porcentaje "
                + codigoPorcentaje + ". Los periodos de vigencia deben ser disjuntos.");
    }
}
