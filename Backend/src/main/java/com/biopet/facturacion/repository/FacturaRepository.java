package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.OrigenDetalleFactura;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    /**
     * Mismo bloqueo de fila, para generar los documentos (XML, y mas adelante
     * XML firmado y RIDE) de una factura ya emitida.
     *
     * <p>Es una consulta identica a {@link #bloquearParaEmitir(Long)} y la
     * duplicacion es deliberada: son dos operaciones distintas que se serializan
     * sobre la misma fila por motivos distintos, y un unico metodo llamado
     * "bloquearParaEmitir" invocado desde la generacion de XML mentiria sobre lo
     * que hace. El nombre de un metodo que toma un lock deberia decir por que lo
     * toma.
     *
     * <p>Aqui protege la unicidad de {@code (factura_id, tipo)} en
     * {@code factura_documentos}: dos peticiones simultaneas de generar el XML de
     * la misma factura se serializan, y la segunda encuentra el documento ya
     * guardado en lugar de chocar contra el indice unico.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Factura f where f.id = :id")
    Optional<Factura> bloquearParaGenerarDocumentos(@Param("id") Long id);

    /**
     * Mismo bloqueo de fila, para persistir el resultado de una llamada al SRI.
     *
     * <p>Tercera copia de la misma consulta, y por el mismo criterio que la
     * anterior: el nombre de un metodo que toma un lock debe decir POR QUE lo
     * toma. Aqui protege la coherencia del estado frente al SRI. Sin el, dos
     * hilos que vuelven a la vez -uno de recepcion y otro de una sincronizacion
     * manual- podrian leer la misma factura, decidir sobre la version antigua y
     * escribir uno encima del otro; el peor caso es una consulta PPR tardia
     * pisando un AUT que acababa de llegar.
     *
     * <p>El lock se toma DESPUES de la llamada SOAP, nunca antes: la fase entera
     * se apoya en que ninguna transaccion siga abierta mientras se espera al
     * SRI. Ver {@code FacturaSriEstadoService}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Factura f where f.id = :id")
    Optional<Factura> bloquearParaSincronizarConSri(@Param("id") Long id);

    /**
     * Fase 8A: listado paginado con los cuatro filtros simples que pidio la
     * fase (estado, usuario, mascota, fecha), todos opcionales. Deliberadamente
     * NO es un motor de busqueda: cuatro comparaciones exactas, cada una
     * activada solo si su parametro no es nulo. Nada de texto libre, rangos
     * complejos ni ordenacion configurable.
     *
     * <p>El filtro de ownership para DUENO (forzar {@code usuarioId} al propio
     * y {@code estado} a AUTORIZADA) NO vive aqui: lo aplica
     * {@code FacturaConsultaService} antes de llamar a este metodo,
     * sustituyendo lo que el cliente haya pedido. Esta consulta no sabe quien
     * la esta llamando. Tampoco la usa VETERINARIO: para el existe
     * {@link #buscarRelacionadasConVeterinario}, con una restriccion distinta
     * que no encaja en estos cuatro filtros.
     */
    @Query("""
            select f from Factura f
            where (:estado is null or f.estado = :estado)
              and (:usuarioId is null or f.usuario.id = :usuarioId)
              and (:mascotaId is null or f.mascota.id = :mascotaId)
              and (:fechaEmision is null or f.fechaEmision = :fechaEmision)
            order by f.fechaEmision desc, f.id desc
            """)
    Page<Factura> buscar(@Param("estado") EstadoFactura estado,
                          @Param("usuarioId") Long usuarioId,
                          @Param("mascotaId") Long mascotaId,
                          @Param("fechaEmision") LocalDate fechaEmision,
                          Pageable pageable);

    /**
     * Correccion pre-commit de la Fase 8A: facturas con al menos UNA linea cuyo
     * origen clinico esta asignado a este veterinario.
     *
     * <h2>Por que solo CONSULTA y CITA</h2>
     *
     * <p>{@code Consulta.veterinario} y {@code Cita.veterinario} son
     * {@code nullable = false}: toda fila de esas tablas tiene, sin ambiguedad,
     * un veterinario responsable. {@code Vacuna.veterinario} es OPCIONAL en el
     * modelo actual ({@code @JoinColumn} sin {@code nullable = false}): una
     * vacuna puede no tener veterinario asignado, asi que "esta vacuna es de
     * este veterinario" no siempre se puede demostrar. Ante esa ambiguedad se
     * aplica la opcion conservadora que pide la fase: no conceder acceso por
     * ese origen. Una factura sin origen clinico en ninguna linea (solo
     * productos) tampoco es relacionada con ningun veterinario.
     *
     * <p>{@code distinct} porque una factura puede tener varias lineas con
     * origen en la misma consulta/cita (no deberia duplicarse en el listado
     * por eso). {@code countQuery} explicito porque el conteo derivado
     * automaticamente de una consulta con {@code join} + {@code distinct}
     * puede contar de mas si no se le pide tambien distinct.
     */
    @Query(value = """
            select distinct f from Factura f
            join f.detalles d
            where (:estado is null or f.estado = :estado)
              and (:mascotaId is null or f.mascota.id = :mascotaId)
              and (:fechaEmision is null or f.fechaEmision = :fechaEmision)
              and (
                (d.origenTipo = :origenConsulta and d.origenId in
                    (select c.id from Consulta c where c.veterinario.id = :veterinarioId))
                or (d.origenTipo = :origenCita and d.origenId in
                    (select ci.id from Cita ci where ci.veterinario.id = :veterinarioId))
              )
            order by f.fechaEmision desc, f.id desc
            """,
            countQuery = """
            select count(distinct f) from Factura f
            join f.detalles d
            where (:estado is null or f.estado = :estado)
              and (:mascotaId is null or f.mascota.id = :mascotaId)
              and (:fechaEmision is null or f.fechaEmision = :fechaEmision)
              and (
                (d.origenTipo = :origenConsulta and d.origenId in
                    (select c.id from Consulta c where c.veterinario.id = :veterinarioId))
                or (d.origenTipo = :origenCita and d.origenId in
                    (select ci.id from Cita ci where ci.veterinario.id = :veterinarioId))
              )
            """)
    Page<Factura> buscarRelacionadasConVeterinario(@Param("estado") EstadoFactura estado,
                                                    @Param("mascotaId") Long mascotaId,
                                                    @Param("fechaEmision") LocalDate fechaEmision,
                                                    @Param("veterinarioId") Long veterinarioId,
                                                    @Param("origenConsulta") OrigenDetalleFactura origenConsulta,
                                                    @Param("origenCita") OrigenDetalleFactura origenCita,
                                                    Pageable pageable);

    /**
     * Igual que {@link #buscarRelacionadasConVeterinario}, pero para UNA
     * factura concreta: la usa el detalle ({@code GET /{id}}) para decidir
     * 403/200 sin traer nada mas que un booleano.
     */
    @Query("""
            select case when count(d) > 0 then true else false end
            from Factura f join f.detalles d
            where f.id = :facturaId
              and (
                (d.origenTipo = :origenConsulta and d.origenId in
                    (select c.id from Consulta c where c.veterinario.id = :veterinarioId))
                or (d.origenTipo = :origenCita and d.origenId in
                    (select ci.id from Cita ci where ci.veterinario.id = :veterinarioId))
              )
            """)
    boolean existeRelacionConVeterinario(@Param("facturaId") Long facturaId,
                                         @Param("veterinarioId") Long veterinarioId,
                                         @Param("origenConsulta") OrigenDetalleFactura origenConsulta,
                                         @Param("origenCita") OrigenDetalleFactura origenCita);
}
