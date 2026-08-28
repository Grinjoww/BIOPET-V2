package com.biopet.facturacion.repository;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.entity.TarifaImpuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TarifaImpuestoRepository extends JpaRepository<TarifaImpuesto, Long> {

    List<TarifaImpuesto> findAllByActivoTrue();

    Optional<TarifaImpuesto> findByCodigoImpuestoAndCodigoPorcentajeAndVigenteHastaIsNull(
            CodigoImpuestoSri codigoImpuesto, String codigoPorcentaje);

    /**
     * Tarifas activas cuyo periodo de vigencia cubre {@code fecha} para el par
     * (codigo de impuesto, codigo de porcentaje). {@code vigenteHasta} nulo
     * significa "vigente indefinidamente".
     *
     * <p>Devuelve LISTA a proposito, no {@code Optional} ni un
     * {@code findTopBy...OrderBy...}. Con una configuracion correcta los
     * periodos son disjuntos y siempre habra 0 o 1 resultado; si hay 2, la
     * configuracion esta mal y quien llama debe enterarse. Un
     * {@code findTopBy...} devolveria la primera y emitiria comprobantes con la
     * tarifa que decidiese un ORDER BY, escondiendo el problema justo donde mas
     * caro sale: en el importe de un documento tributario ya emitido.
     */
    @Query("select t from TarifaImpuesto t "
            + "where t.activo = true "
            + "and t.codigoImpuesto = :codigoImpuesto "
            + "and t.codigoPorcentaje = :codigoPorcentaje "
            + "and t.vigenteDesde <= :fecha "
            + "and (t.vigenteHasta is null or t.vigenteHasta >= :fecha)")
    List<TarifaImpuesto> findAplicables(@Param("codigoImpuesto") CodigoImpuestoSri codigoImpuesto,
                                        @Param("codigoPorcentaje") String codigoPorcentaje,
                                        @Param("fecha") LocalDate fecha);
}
