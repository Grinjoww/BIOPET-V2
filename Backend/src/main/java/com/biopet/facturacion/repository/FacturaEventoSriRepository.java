package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.FacturaEventoSri;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacturaEventoSriRepository extends JpaRepository<FacturaEventoSri, Long> {

    /** Bitacora de una factura, del evento mas reciente al mas antiguo. */
    List<FacturaEventoSri> findAllByFactura_IdOrderByCreadoEnDesc(Long facturaId);
}
