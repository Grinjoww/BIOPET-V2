package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.FacturaPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacturaPagoRepository extends JpaRepository<FacturaPago, Long> {

    List<FacturaPago> findAllByFactura_Id(Long facturaId);
}
