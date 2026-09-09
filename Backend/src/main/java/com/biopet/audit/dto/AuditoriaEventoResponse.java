package com.biopet.audit.dto;

import java.time.Instant;

public record AuditoriaEventoResponse(
        Long id,
        Instant fechaHora,
        String usuarioEmail,
        String accion,
        String modulo,
        String recurso,
        String metodoHttp,
        String resultado,
        Integer statusHttp
) {}
