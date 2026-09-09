package com.biopet.backup.pgdump;

import java.nio.file.Path;

/**
 * Todo lo que PgDumpExecutor necesita para un respaldo. {@code password}
 * SOLO se usa para poblar la variable de entorno PGPASSWORD del proceso
 * hijo -jamas se agrega a la lista de argumentos ni se registra en ningun
 * log; ver PgDumpExecutorImpl-.
 */
public record PgDumpParametros(
        String host,
        int port,
        String database,
        String usuario,
        String password,
        Path rutaSalida
) {}
