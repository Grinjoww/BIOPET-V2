package com.biopet.backup.pgdump;

/** Categorias fijas de fallo de pg_dump -nunca se expone el detalle crudo del proceso. */
public enum TipoFalloPgDump {
    /** pg_dump no termino dentro de backup.timeout-seconds; el proceso se cancela. */
    TIMEOUT,
    /** pg_dump termino pero con codigo de salida distinto de 0. */
    CODIGO_SALIDA,
    /** No se pudo ni lanzar el proceso (ejecutable no encontrado, permisos, disco). */
    ERROR_E_S,
    /** El hilo que esperaba a pg_dump fue interrumpido (apagado del backend). */
    INTERRUMPIDO,
    /**
     * La validacion previa "pg_dump --version" fallo: el ejecutable
     * configurado (backup.pg-dump-path / PG_DUMP_PATH) no existe, no tiene
     * permiso de ejecucion, o no respondio -tipicamente por portar el
     * proyecto a otra maquina sin fijar PG_DUMP_PATH y sin un pg_dump
     * compatible en el PATH del sistema.
     */
    EJECUTABLE_NO_DISPONIBLE
}
