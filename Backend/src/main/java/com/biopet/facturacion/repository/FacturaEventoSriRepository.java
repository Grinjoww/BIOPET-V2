package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.FacturaEventoSri;
import com.biopet.facturacion.entity.OperacionSri;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacturaEventoSriRepository extends JpaRepository<FacturaEventoSri, Long> {

    /** Bitacora de una factura, del evento mas reciente al mas antiguo. */
    List<FacturaEventoSri> findAllByFactura_IdOrderByCreadoEnDesc(Long facturaId);

    /**
     * Cuantos intentos lleva ya esta factura en una operacion concreta.
     *
     * <p>Se usa para numerar el campo {@code intento} de la bitacora, que empieza
     * en 1. Es un COUNT y no un MAX+1 a proposito: la tabla es append-only y
     * nunca se borra de ella, asi que ambos coinciden, y el COUNT no depende de
     * que el campo previo se hubiera escrito bien.
     */
    long countByFactura_IdAndOperacion(Long facturaId, OperacionSri operacion);

    /**
     * Bitacora de una operacion concreta, del evento mas reciente al mas antiguo.
     * La usan las pruebas y, mas adelante, la consulta de trazabilidad.
     */
    List<FacturaEventoSri> findAllByFactura_IdAndOperacionOrderByCreadoEnDesc(
            Long facturaId, OperacionSri operacion);
}
