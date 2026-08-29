package com.biopet.facturacion.dto;

/**
 * Vista compacta de {@code PuntoEmision}. Deliberadamente NO incluye ningun
 * dato de {@code SecuencialEmision} (ni ultimo ni siguiente secuencial, ni
 * ambiente): esa numeracion sigue siendo exclusivamente interna del pipeline de
 * emision, nunca de un catalogo de seleccion REST.
 */
public record PuntoEmisionResponse(
        Long id,
        Long emisorFiscalId,
        String establecimiento,
        String puntoEmision,
        String direccionEstablecimiento,
        boolean activo
) {
}
