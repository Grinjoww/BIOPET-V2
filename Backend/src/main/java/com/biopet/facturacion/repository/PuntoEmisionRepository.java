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
}
