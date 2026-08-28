package com.biopet.facturacion.entity;

/**
 * Tipo de registro clinico que dio origen a una linea de factura.
 *
 * <p>Va en el DETALLE y no en la cabecera a proposito: una misma factura puede
 * mezclar una consulta, dos vacunas y un producto sin origen clinico. La
 * referencia es debil ({@code origen_tipo} + {@code origen_id}, sin FK
 * polimorfica) para no acoplar el modulo fiscal a las tablas clinicas, que esta
 * fase no modifica.
 */
public enum OrigenDetalleFactura {
    CONSULTA,
    VACUNA,
    CITA
}
