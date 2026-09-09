package com.biopet.audit.dto;

import java.util.List;

/**
 * Alimenta los selects "Usuario", "Acción" y "Módulo" de la pantalla de
 * auditoría con valores REALMENTE presentes en auditoria_evento -no una
 * lista hardcodeada ni el listado completo de usuarios registrados.
 */
public record AuditoriaOpcionesResponse(List<String> usuarios, List<String> acciones, List<String> modulos) {}
