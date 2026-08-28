package com.biopet.facturacion.entity;

/**
 * Estado devuelto por el web service de AUTORIZACION del SRI (Ficha v2.34,
 * seccion 8). Se guardan los codigos tal y como los publica el SRI:
 *
 * <ul>
 *   <li>{@code PPR} - en procesamiento; hay que volver a consultar.</li>
 *   <li>{@code AUT} - autorizado.</li>
 *   <li>{@code NAT} - no autorizado.</li>
 * </ul>
 */
public enum EstadoAutorizacionSri {
    PPR,
    AUT,
    NAT
}
