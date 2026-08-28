package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.DatosFacturacion;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
