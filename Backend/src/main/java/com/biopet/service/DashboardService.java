package com.biopet.service;

import com.biopet.dto.DashboardResumenResponse;
import com.biopet.repository.ProcedimientoBiopetRepository;
import com.biopet.repository.ReporteDashboard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Integración mínima de {@code fn_reporte_dashboard} (existente desde
 * V5/V6, nunca antes expuesta por HTTP — confirmado auditando los 7
 * controllers reales antes de esta fase). No reescribe en Java ninguna
 * consulta que ya vive en el procedimiento SQL: solo invoca
 * {@link ProcedimientoBiopetRepository#reporteDashboard} y traduce su
 * resultado a un DTO explícito.
 *
 * Sin caché deliberadamente: cachear un resumen operativo que depende de
 * escrituras en Mascota/Cita/Consulta/Vacuna obligaría a evictar esa
 * misma caché desde otros 4 servicios ya existentes, una dependencia
 * cruzada que este endpoint no justifica todavía.
 */
@Service
public class DashboardService {
    private final ProcedimientoBiopetRepository procedimientoBiopetRepository;

    public DashboardService(ProcedimientoBiopetRepository procedimientoBiopetRepository) {
        this.procedimientoBiopetRepository = procedimientoBiopetRepository;
    }

    /**
     * El procedimiento SQL no valida el rango (una inversión simplemente
     * produce ceros en los campos acotados por rango — verificado en
     * ProcedimientosBiopetIntegrationTest.reporteDashboard_rangoInvertido_noCuentaDentroDelRango).
     * Rechazamos esa inversión aquí con 400 en vez de devolver un
     * resumen silenciosamente vacío que parecería un dato real.
     */
    @Transactional(readOnly = true)
    public DashboardResumenResponse resumen(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }

        List<ReporteDashboard> resultado = procedimientoBiopetRepository.reporteDashboard(desde, hasta);
        ReporteDashboard r = resultado.get(0);

        return new DashboardResumenResponse(
                desde,
                hasta,
                r.getMascotasActivas(),
                r.getCitasProgramadas(),
                r.getConsultasEnRango(),
                r.getVacunasEnRango(),
                r.getMascotasSinConsulta()
        );
    }
}
