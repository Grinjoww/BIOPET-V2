package com.biopet.facturacion.service.command;

import com.biopet.facturacion.domain.AmbienteSri;

/**
 * Orden de emision local de un borrador.
 *
 * <p>El ambiente se recibe EXPLICITAMENTE y no se lee de una variable de
 * entorno. En esta fase eso mantiene los tests deterministas y permite ejercitar
 * los dos contadores; cuando exista la configuracion SRI del despliegue, sera
 * esa capa la que decida el ambiente y lo pase aqui, no este servicio quien lo
 * adivine.
 *
 * @param facturaId      borrador a emitir.
 * @param puntoEmisionId punto de emision activo desde el que se numera.
 * @param ambiente       PRUEBAS o PRODUCCION; cada uno lleva su propio contador.
 */
public record EmitirFacturaCommand(Long facturaId, Long puntoEmisionId, AmbienteSri ambiente) {
}
