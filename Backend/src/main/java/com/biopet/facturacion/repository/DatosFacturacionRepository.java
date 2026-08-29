package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.DatosFacturacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DatosFacturacionRepository extends JpaRepository<DatosFacturacion, Long> {

    List<DatosFacturacion> findAllByUsuario_IdAndActivoTrue(Long usuarioId);

    /**
     * Identidad tributaria por defecto del usuario. Devuelve Optional y no una
     * lista porque el indice unico parcial garantiza que como mucho hay una.
     */
    Optional<DatosFacturacion> findByUsuario_IdAndPredeterminadoTrueAndActivoTrue(Long usuarioId);

    /**
     * Identidad tributaria activa que pertenece a ese usuario. El id del usuario
     * va en la consulta, no en un {@code if} posterior: asi no existe el camino
     * en el que se cargan los datos de otro y alguien se olvida de comprobar el
     * duenio.
     */
    Optional<DatosFacturacion> findByIdAndUsuario_IdAndActivoTrue(Long id, Long usuarioId);

    /**
     * Paso 1 del cambio de predeterminado (Fase 8B): apaga cualquier
     * predeterminado previo del usuario. Bulk UPDATE, no un
     * {@code find + setPredeterminado(false) + save} por fila: es una unica
     * sentencia SQL y no depende de que el contexto de persistencia tenga ya
     * cargadas las filas a apagar.
     *
     * <p>Que dos peticiones concurrentes intenten fijar DOS predeterminados
     * distintos para el mismo usuario NO lo evita este metodo por si solo -ver
     * el javadoc de {@code DatosFacturacionService#aplicarPredeterminado}-: la
     * garantia final la da {@code idx_datos_facturacion_predeterminado_unico}
     * (indice unico parcial de V7), que hace fallar con
     * {@code DataIntegrityViolationException} (409, ver
     * {@code GlobalExceptionHandler}) a quien pierda la carrera en el paso 2.
     */
    @Modifying(clearAutomatically = true)
    @Query("update DatosFacturacion d set d.predeterminado = false "
            + "where d.usuario.id = :usuarioId and d.predeterminado = true")
    int limpiarPredeterminado(@Param("usuarioId") Long usuarioId);

    /**
     * Paso 2: fija el nuevo predeterminado, con el usuario y la condicion
     * {@code activo} en la MISMA sentencia -no en un {@code if} posterior en
     * Java-, igual que el resto de operaciones de este repositorio con
     * ownership. Devuelve 0 si {@code id} no existe, no esta activo o no
     * pertenece a {@code usuarioId}: el servicio lo traduce a 404.
     */
    @Modifying(clearAutomatically = true)
    @Query("update DatosFacturacion d set d.predeterminado = true "
            + "where d.id = :id and d.usuario.id = :usuarioId and d.activo = true")
    int marcarPredeterminado(@Param("id") Long id, @Param("usuarioId") Long usuarioId);
}
