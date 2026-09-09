package com.biopet.backup.entity;

/** Espejo de chk_respaldo_historial_estado (V9). */
public enum EstadoRespaldo {
    /** pg_dump fue lanzado y todavia no termino. */
    EN_PROCESO,
    /** pg_dump termino con codigo de salida 0 y el archivo quedo en disco. */
    EXITOSO,
    /** pg_dump no pudo completarse (timeout, codigo de salida distinto de 0, error de E/S). */
    FALLIDO
}
