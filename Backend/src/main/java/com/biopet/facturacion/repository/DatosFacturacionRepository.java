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
}
