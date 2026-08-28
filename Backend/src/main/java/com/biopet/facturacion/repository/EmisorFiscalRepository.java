package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.EmisorFiscal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Fase 4A: solo lo minimo para validar el modelo persistente. Sin filtros
 * complejos y sin ninguna consulta de bloqueo.
 */
public interface EmisorFiscalRepository extends JpaRepository<EmisorFiscal, Long> {

    Optional<EmisorFiscal> findByRuc(String ruc);

    List<EmisorFiscal> findAllByActivoTrue();
}
