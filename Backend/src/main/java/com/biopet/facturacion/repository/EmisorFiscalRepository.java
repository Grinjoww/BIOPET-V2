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

    /**
     * "El" emisor configurado, para la Fase 8B: BIOPET modela una sola clinica,
     * asi que {@code GET/PUT /api/facturacion/emisor} operan sobre una fila
     * unica en lugar de una coleccion. Se pide por {@code id} ascendente y no
     * "el unico que exista" para que, si alguna vez aparece una segunda fila por
     * un camino distinto a este endpoint (nunca por aqui: {@code EmisorFiscalService}
     * jamas inserta una segunda vez si ya hay una), el servicio siga
     * devolviendo un resultado determinista en vez de lanzar una excepcion de
     * Spring Data por resultado no unico.
     */
    Optional<EmisorFiscal> findFirstByOrderByIdAsc();
}
