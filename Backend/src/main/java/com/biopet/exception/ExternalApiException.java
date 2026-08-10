package com.biopet.exception;

public class ExternalApiException extends RuntimeException {
    public ExternalApiException(String mensaje) {
        super(mensaje);
    }

    public ExternalApiException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}