package com.biopet.backup.entity;

/** Quien origino el intento de respaldo. Espejo de chk_respaldo_historial_tipo (V9). */
public enum TipoRespaldo {
    /** Disparado por un ROLE_ADMIN desde "Generar respaldo ahora". */
    MANUAL,
    /** Disparado por RespaldoScheduler al cumplirse proximoRespaldoEn. */
    AUTOMATICO
}
