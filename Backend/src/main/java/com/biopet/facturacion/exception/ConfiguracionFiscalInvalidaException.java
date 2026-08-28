package com.biopet.facturacion.exception;

/**
 * La configuracion de emision no permite numerar: el punto de emision esta
 * inactivo, su emisor esta inactivo, o falta algun dato tributario obligatorio
 * del emisor.
 *
 * <p>Distinta de {@code SecuencialNoConfiguradoException} (Fase 4B), que cubre
 * el caso concreto de que exista el punto pero no su contador para el ambiente
 * pedido.
 */
public class ConfiguracionFiscalInvalidaException extends RuntimeException {

    public ConfiguracionFiscalInvalidaException(String mensaje) {
        super(mensaje);
    }
}
