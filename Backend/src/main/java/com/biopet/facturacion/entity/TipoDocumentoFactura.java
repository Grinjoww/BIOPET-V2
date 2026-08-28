package com.biopet.facturacion.entity;

/**
 * Artefacto binario asociado a una factura. Hay como maximo uno de cada tipo
 * por factura (constraint {@code uq_factura_documentos_tipo}).
 */
public enum TipoDocumentoFactura {
    /** XML generado a partir del modelo, todavia sin firmar. */
    XML_GENERADO,
    /** XML con la firma XAdES-BES incrustada (ver el spike de la Fase 3). */
    XML_FIRMADO,
    /** Respuesta de autorizacion del SRI, con el comprobante embebido. */
    XML_AUTORIZADO,
    /** Representacion impresa (RIDE) en PDF. */
    RIDE_PDF
}
