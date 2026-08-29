package com.biopet.facturacion.dto;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.entity.TipoConceptoFacturable;

import java.math.BigDecimal;

/**
 * Vista compacta de {@code ConceptoFacturable} para el catalogo del futuro
 * frontend: exactamente lo que hace falta para poblar un selector de lineas y
 * mostrar el precio/impuesto vigentes del catalogo, nada de auditoria interna.
 */
public record ConceptoFacturableResponse(
        Long id,
        String codigo,
        String descripcion,
        TipoConceptoFacturable tipo,
        BigDecimal precioUnitario,
        CodigoImpuestoSri codigoImpuesto,
        String codigoPorcentaje,
        boolean activo
) {
}
