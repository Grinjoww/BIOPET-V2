package com.biopet.facturacion.entity;

/**
 * Clasificacion interna de BIOPET de lo que se puede facturar. NO es un
 * catalogo del SRI: al comprobante solo viajan el codigo, la descripcion y los
 * codigos de impuesto. Sirve para agrupar el catalogo en la interfaz y para
 * relacionar un concepto con el origen clinico que lo genera.
 */
public enum TipoConceptoFacturable {
    CONSULTA,
    VACUNA,
    PROCEDIMIENTO,
    MEDICAMENTO,
    PRODUCTO,
    OTRO
}
