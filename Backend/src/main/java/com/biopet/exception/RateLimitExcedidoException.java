package com.biopet.exception;

public class RateLimitExcedidoException extends RuntimeException {

    public enum Recurso {
        LOGIN, REGISTRO
    }

    private final long segundosRestantes;
    private final Recurso recurso;

    public RateLimitExcedidoException(long segundosRestantes) {
        this(segundosRestantes, Recurso.LOGIN);
    }

    public RateLimitExcedidoException(long segundosRestantes, Recurso recurso) {
        super(mensaje(recurso, segundosRestantes));
        this.segundosRestantes = segundosRestantes;
        this.recurso = recurso;
    }

    private static String mensaje(Recurso recurso, long segundosRestantes) {
        String accion = (recurso == Recurso.REGISTRO) ? "de registro" : "de inicio de sesión";
        return "Se ha excedido el número máximo de intentos fallidos " + accion + ". Intente nuevamente en "
                + segundosRestantes + " segundos.";
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }

    public Recurso getRecurso() {
        return recurso;
    }
}
