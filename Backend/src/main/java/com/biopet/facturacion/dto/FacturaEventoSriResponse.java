package com.biopet.facturacion.dto;

import com.biopet.facturacion.entity.OperacionSri;
import com.biopet.facturacion.entity.ResultadoEventoSri;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Una fila de la bitacora {@code factura_eventos_sri}, para
 * {@code GET /api/facturas/{id}/eventos-sri}.
 *
 * <p>{@code mensajes} se expone como JSON estructurado ({@link JsonNode}), no
 * como el texto crudo de la columna JSONB: son los mensajes FUNCIONALES del
 * SRI (identificador, mensaje, informacionAdicional, tipo), nunca el sobre
 * SOAP completo -eso nunca se persistio, ver {@code FacturaSriEstadoService}-
 * y nunca hay un secreto en esta tabla que filtrar.
 */
public record FacturaEventoSriResponse(
        Long id,
        OperacionSri operacion,
        ResultadoEventoSri resultado,
        JsonNode mensajes,
        Long duracionMs,
        Integer intento,
        Instant creadoEn
) {
}
