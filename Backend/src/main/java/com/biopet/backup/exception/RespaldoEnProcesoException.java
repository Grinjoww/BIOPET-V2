package com.biopet.backup.exception;

/**
 * Ya hay un respaldo EN_PROCESO (manual o automatico) cuando se pidio
 * disparar otro. GlobalExceptionHandler la traduce a 409 CONFLICT.
 */
public class RespaldoEnProcesoException extends RuntimeException {
    public RespaldoEnProcesoException(String mensaje) {
        super(mensaje);
    }
}
