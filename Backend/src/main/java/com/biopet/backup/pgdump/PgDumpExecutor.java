package com.biopet.backup.pgdump;

/**
 * Frontera de proceso externo, aislada en una interfaz para poder
 * mockearla en los tests del modulo (RespaldoEjecucionServiceTest,
 * RespaldoControllerTest...) sin lanzar un pg_dump real. La unica
 * implementacion de produccion es PgDumpExecutorImpl.
 */
public interface PgDumpExecutor {

    /**
     * Ejecuta pg_dump -Fc contra la base descrita en {@code parametros},
     * escribiendo el resultado en {@code parametros.rutaSalida()}.
     *
     * @throws PgDumpExecutionException si el proceso no pudo completarse
     *                                   correctamente (ver TipoFalloPgDump).
     */
    void ejecutar(PgDumpParametros parametros);
}
