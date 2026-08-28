package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Fase 4A: consultas minimas para validar el modelo.
 *
 * <p>Notese que NINGUN metodo lleva "ActivoTrue", a diferencia del resto de
 * repositories del proyecto: la tabla {@code facturas} no tiene columna
 * {@code activo} a proposito, y el filtro natural es el estado. Un comprobante
 * emitido nunca se oculta.
 */
public interface FacturaRepository extends JpaRepository<Factura, Long> {

    Optional<Factura> findByClaveAcceso(String claveAcceso);

    /** Apoyada en idx_facturas_usuario_estado_fecha. */
    Page<Factura> findAllByUsuario_IdAndEstadoOrderByFechaEmisionDesc(
            Long usuarioId, EstadoFactura estado, Pageable pageable);

    List<Factura> findAllByUsuario_Id(Long usuarioId);

    boolean existsByClaveAcceso(String claveAcceso);
}
