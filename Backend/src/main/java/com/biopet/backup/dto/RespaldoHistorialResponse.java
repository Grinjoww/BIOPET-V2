package com.biopet.backup.dto;

import com.biopet.backup.entity.EstadoRespaldo;
import com.biopet.backup.entity.TipoRespaldo;

import java.time.Instant;

public record RespaldoHistorialResponse(
        Long id,
        TipoRespaldo tipo,
        EstadoRespaldo estado,
        Instant iniciadoEn,
        Instant finalizadoEn,
        Long duracionMs,
        String nombreArchivo,
        Long tamanoBytes,
        String mensajeErrorSeguro,
        String ejecutadoPor
) {}
