package com.biopet.facturacion.dto;

import com.biopet.facturacion.domain.CodigoImpuestoSri;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vista compacta de una vigencia de {@code TarifaImpuesto}. Incluye
 * {@code vigenteHasta} (null = vigente indefinidamente) porque el frontend
 * necesita distinguir una vigencia abierta de una ya cerrada, no solo el
 * porcentaje actual.
 */
public record TarifaImpuestoResponse(
        Long id,
        CodigoImpuestoSri codigoImpuesto,
        String codigoPorcentaje,
        String descripcion,
        BigDecimal tarifa,
        LocalDate vigenteDesde,
        LocalDate vigenteHasta,
        boolean activo
) {
}
