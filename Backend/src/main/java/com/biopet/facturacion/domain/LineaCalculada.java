package com.biopet.facturacion.domain;

import java.math.BigDecimal;

/**
 * Resultado del calculo de una linea. Todos los importes ya vienen en la escala
 * monetaria (2 decimales) con la que viajaran al XML.
 *
 * @param origen                  la linea de entrada, para trazar el calculo.
 * @param precioTotalSinImpuesto  {@code <precioTotalSinImpuesto>} del detalle.
 * @param baseImponible           {@code <baseImponible>} del impuesto de la linea.
 * @param valorImpuesto           {@code <valor>} del impuesto de la linea.
 * @param totalLinea              base + impuesto. No existe como tag en el XSD;
 *                                se expone solo para la UI y los totales.
 */
public record LineaCalculada(
        LineaFacturable origen,
        BigDecimal precioTotalSinImpuesto,
        BigDecimal baseImponible,
        BigDecimal valorImpuesto,
        BigDecimal totalLinea
) {
}
