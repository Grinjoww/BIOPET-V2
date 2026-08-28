package com.biopet.facturacion.exception;

import com.biopet.facturacion.domain.CodigoImpuestoSri;

import java.time.LocalDate;

/**
 * No hay ninguna tarifa vigente para el par (codigo de impuesto, codigo de
 * porcentaje) en la fecha de emision.
 *
 * <p>Es un error de CONFIGURACION, no de la factura: la tabla de tarifas nace
 * vacia a proposito (V7 no siembra ningun porcentaje) y alguien debe poblarla.
 * El borrador puede seguir existiendo; lo que no puede es emitirse.
 */
public class TarifaImpuestoNoConfiguradaException extends RuntimeException {

    public TarifaImpuestoNoConfiguradaException(CodigoImpuestoSri codigoImpuesto,
                                                String codigoPorcentaje,
                                                LocalDate fecha) {
        super("No hay tarifa vigente para el impuesto " + codigoImpuesto.codigo()
                + " con codigo de porcentaje " + codigoPorcentaje + " en la fecha " + fecha
                + ". Configure la tarifa antes de emitir.");
    }
}
