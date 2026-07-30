package com.biopet.exception;

public class RateLimitExcedidoException extends RuntimeException {
    private final long segundosRestantes;

    public RateLimitExcedidoException(long segundosRestantes) {
        super("Se ha excedido el número máximo de intentos fallidos de inicio de sesión. Intente nuevamente en "
                + segundosRestantes + " segundos.");
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }
}
