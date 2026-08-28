package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Carga la factura tomando un bloqueo pesimista de escritura sobre su fila.
     * Uso EXCLUSIVO de la emision.
     *
     * <p>Por que hace falta, teniendo ya el lock del contador (Fase 4B): aquel
     * garantiza que dos emisiones nunca reciben el mismo numero, pero no impide
     * que la MISMA factura se emita dos veces y consuma dos numeros distintos.
     * Bloqueando la fila de la factura, el segundo intento espera al primero y,
     * al entrar, ya la ve EMITIDA: devuelve esa emision en lugar de numerar otra
     * vez.
     *
     * <p>Se declara aparte, y no se cambia {@code findById}, porque el resto de
     * la aplicacion (listados, consultas de detalle) no debe bloquear nada.
     * Consulta sin JOIN: el {@code FOR UPDATE} alcanza solo a {@code facturas}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Factura f where f.id = :id")
    Optional<Factura> bloquearParaEmitir(@Param("id") Long id);
}
