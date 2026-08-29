package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.PuntoEmision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PuntoEmisionRepository extends JpaRepository<PuntoEmision, Long> {

    /** Localiza la serie 001-001 de un emisor concreto. */
    Optional<PuntoEmision> findByEmisorFiscal_IdAndEstablecimientoAndPuntoEmision(
            Long emisorFiscalId, String establecimiento, String puntoEmision);

    List<PuntoEmision> findAllByEmisorFiscal_IdAndActivoTrue(Long emisorFiscalId);

    /**
     * Todos los puntos activos, de cualquier emisor. Usada por la Fase 8B para
     * que AUXILIAR pueda elegir {@code puntoEmisionId} al emitir sin necesitar
     * primero el id del emisor: hoy BIOPET tiene una sola clinica, pero el
     * filtro por emisor de {@link #findAllByEmisorFiscal_IdAndActivoTrue} sigue
     * disponible para cuando haga falta acotar.
     */
    List<PuntoEmision> findAllByActivoTrue();
}
