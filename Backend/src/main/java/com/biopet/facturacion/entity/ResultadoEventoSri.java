package com.biopet.facturacion.entity;

/**
 * Desenlace de un intento contra el SRI.
 *
 * <p>Reune en un solo enum los resultados de negocio de ambas operaciones
 * ({@code RECIBIDA}/{@code DEVUELTA} para recepcion, {@code AUT}/{@code NAT}/
 * {@code PPR} para autorizacion) y los dos desenlaces tecnicos en los que el
 * SRI no llega a responder nada util. Distinguir ERROR_TECNICO y TIMEOUT de un
 * rechazo real importa: un rechazo es definitivo, un fallo tecnico se
 * reintenta.
 */
public enum ResultadoEventoSri {
    RECIBIDA,
    DEVUELTA,
    AUT,
    NAT,
    PPR,
    ERROR_TECNICO,
    TIMEOUT
}
