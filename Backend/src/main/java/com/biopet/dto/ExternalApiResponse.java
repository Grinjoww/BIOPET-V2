package com.biopet.dto;

import java.io.Serializable;
import java.time.Instant;

public record ExternalApiResponse(
        String especieConsultada,
        String nombreCientifico,
        String habitat,
        String dieta,
        String origen,        // "cache" o "api-ninjas"
        Instant consultadoEn
) implements Serializable {}