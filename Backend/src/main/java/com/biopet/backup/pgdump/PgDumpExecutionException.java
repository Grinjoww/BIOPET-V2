package com.biopet.backup.pgdump;

/**
 * pg_dump no pudo completar el respaldo. {@code getMessage()} es SIEMPRE un
 * texto seguro y generico (nunca stderr crudo, nunca una cadena de
 * conexion) -apto para guardarse tal cual en
 * respaldo_historial.mensaje_error_seguro.
 */
public class PgDumpExecutionException extends RuntimeException {
    private final TipoFalloPgDump tipo;

    public PgDumpExecutionException(TipoFalloPgDump tipo, String mensajeSeguro) {
        super(mensajeSeguro);
        this.tipo = tipo;
    }

    public PgDumpExecutionException(TipoFalloPgDump tipo, String mensajeSeguro, Throwable causa) {
        super(mensajeSeguro, causa);
        this.tipo = tipo;
    }

    public TipoFalloPgDump getTipo() {
        return tipo;
    }
}
