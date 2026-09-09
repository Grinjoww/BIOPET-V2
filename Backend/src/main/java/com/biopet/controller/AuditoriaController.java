package com.biopet.controller;

import com.biopet.audit.dto.AuditoriaEventoResponse;
import com.biopet.audit.dto.AuditoriaOpcionesResponse;
import com.biopet.audit.service.AuditoriaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Auditoria consultable -SOLO ADMIN- de las acciones realizadas por los
 * usuarios: filtra por usuario/accion/modulo/rango de fechas y devuelve
 * resultados PAGINADOS (nunca el historial completo en memoria, ver
 * AuditoriaEventoRepository.buscar). Escritura: ver AuditoriaHttpFilter
 * (generica) y AuditoriaService.registrar (llamado desde
 * AuthenticationAuditService y desde el modulo de respaldos) -este
 * controller es de solo LECTURA.
 */
@RestController
@RequestMapping("/api/admin/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditoriaEventoResponse> buscar(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String modulo,
            // Sin @DateTimeFormat: Spring ya registra un Formatter<Instant>
            // propio (InstantFormatter) que exige el formato ISO-8601 completo
            // ("2026-09-01T00:00:00Z"), la MISMA forma en que Jackson ya
            // serializa cualquier Instant en las respuestas de esta API -el
            // frontend no necesita convertir nada, solo reenviar lo que ya
            // recibio.
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @PageableDefault(size = 20, sort = "fechaHora", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditoriaService.buscar(usuario, accion, modulo, desde, hasta, pageable);
    }

    /** Valores REALMENTE presentes en la auditoria, para poblar los 3 selects del filtro. */
    @GetMapping("/opciones")
    @PreAuthorize("hasRole('ADMIN')")
    public AuditoriaOpcionesResponse opciones() {
        return auditoriaService.opcionesFiltro();
    }
}
