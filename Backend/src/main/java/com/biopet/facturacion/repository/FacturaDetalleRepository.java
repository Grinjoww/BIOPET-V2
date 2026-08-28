package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.FacturaDetalle;
import com.biopet.facturacion.entity.OrigenDetalleFactura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacturaDetalleRepository extends JpaRepository<FacturaDetalle, Long> {

    List<FacturaDetalle> findAllByFactura_IdOrderByLineaAsc(Long facturaId);

    long countByFactura_Id(Long facturaId);

    /**
     * "Que se facturo a partir de esta consulta/vacuna/cita". Apoyada en
     * idx_factura_detalles_origen. Devuelve lista, no Optional: no se prohibe
     * que un mismo origen aparezca en mas de una factura.
     */
    List<FacturaDetalle> findAllByOrigenTipoAndOrigenId(OrigenDetalleFactura origenTipo, Long origenId);
}
