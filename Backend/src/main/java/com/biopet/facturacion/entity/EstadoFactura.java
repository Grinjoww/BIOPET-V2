package com.biopet.facturacion.entity;

/**
 * Ciclo de vida de una factura en BIOPET.
 *
 * <p>Este enum es la UNICA fuente de verdad sobre el estado de una factura: la
 * tabla {@code facturas} no tiene columna {@code activo}, a diferencia del
 * resto de entidades operativas del proyecto. Un comprobante autorizado por el
 * SRI no puede darse de baja logicamente, y una segunda bandera acabaria
 * contradiciendo a esta (ver la nota de diseno en V8__facturas.sql).
 *
 * <p>Las reglas de transicion (que estado puede pasar a cual) pertenecen al
 * futuro servicio de emision, no a la persistencia. La base de datos solo
 * comprueba, con {@code chk_facturas_estado}, que el valor almacenado sea uno
 * de estos cuatro.
 */
public enum EstadoFactura {
    /** Documento interno, sin valor fiscal: sin clave de acceso ni secuencial. */
    BORRADOR,
    /** Ya consumio numeracion fiscal (clave de acceso + secuencial). Inmutable. */
    EMITIDA,
    /** Autorizada por el SRI. */
    AUTORIZADA,
    /** Devuelta en recepcion o no autorizada. */
    RECHAZADA
}
