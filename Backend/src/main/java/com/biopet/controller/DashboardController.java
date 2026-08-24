package com.biopet.controller;

import com.biopet.dto.DashboardResumenResponse;
import com.biopet.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Expone fn_reporte_dashboard (existente en la base desde V5/V6, nunca
 * antes llamada por HTTP). Solo lectura, solo este único endpoint.
 *
 * Roles: ADMIN/VETERINARIO/AUXILIAR únicamente. El procedimiento no
 * admite ningún parámetro de dueño (a diferencia de
 * fn_resumen_mascotas_por_especie) — sus 5 columnas son agregados de
 * TODA la clínica. Exponerlo a ROLE_DUENO filtraría solo en el cliente,
 * exactamente el patrón que este proyecto evita — filtrar en el cliente
 * lo que debe filtrarse en el backend (la propia Corrección A de
 * ConsultaService existe por esta misma razón), así que ROLE_DUENO
 * directamente no tiene acceso a este endpoint.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public DashboardResumenResponse resumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dashboardService.resumen(desde, hasta);
    }
}
