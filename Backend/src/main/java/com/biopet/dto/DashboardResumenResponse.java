package com.biopet.dto;

import java.time.LocalDate;

/**
 * Espejo explícito de las 5 columnas reales de {@code fn_reporte_dashboard}
 * (ver V6__formalizar_procedimientos_jpa.sql), más el rango efectivamente
 * usado para calcularlas (eco de lo que el cliente pidió, no un valor
 * inventado por el backend).
 *
 * IMPORTANTE — no todos los campos están acotados por el rango:
 * {@code mascotasActivas}, {@code citasProgramadas} y
 * {@code mascotasSinConsulta} son agregados SIEMPRE globales (todo el
 * historial activo), independientes de {@code desde}/{@code hasta}. Solo
 * {@code consultasEnRango} y {@code vacunasEnRango} están realmente
 * filtrados por el rango. Esto es literal del procedimiento SQL, no una
 * limitación de este DTO — el frontend no debe presentar los 5 números
 * como si todos pertenecieran al rango seleccionado.
 */
public record DashboardResumenResponse(
        LocalDate desde,
        LocalDate hasta,
        Long mascotasActivas,
        Long citasProgramadas,
        Long consultasEnRango,
        Long vacunasEnRango,
        Long mascotasSinConsulta
) {}
