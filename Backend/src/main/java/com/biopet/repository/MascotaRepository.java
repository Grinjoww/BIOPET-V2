package com.biopet.repository;

import com.biopet.entity.Mascota;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MascotaRepository extends JpaRepository<Mascota, Long> {
    Page<Mascota> findAllByActivoTrue(Pageable pageable);
    Optional<Mascota> findByIdAndActivoTrue(Long id);

    @Query(value = "SELECT * FROM fn_resumen_mascotas_por_especie(:duenioId)", nativeQuery = true)
    List<ResumenEspecie> resumenPorEspecie(@Param("duenioId") Long duenioId);
}
