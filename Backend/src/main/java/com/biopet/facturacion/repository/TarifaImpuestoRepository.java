package com.biopet.facturacion.repository;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.entity.TarifaImpuesto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TarifaImpuestoRepository extends JpaRepository<TarifaImpuesto, Long> {

    List<TarifaImpuesto> findAllByActivoTrue();

    Optional<TarifaImpuesto> findByCodigoImpuestoAndCodigoPorcentajeAndVigenteHastaIsNull(
            CodigoImpuestoSri codigoImpuesto, String codigoPorcentaje);
}
