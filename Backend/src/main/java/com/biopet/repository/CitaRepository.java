package com.biopet.repository;

import com.biopet.entity.Cita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CitaRepository extends JpaRepository<Cita, Long> {
    Page<Cita> findAllByActivoTrue(Pageable pageable);
    Page<Cita> findAllByMascota_Duenio_IdAndActivoTrue(Long duenioId, Pageable pageable);
    Optional<Cita> findByIdAndActivoTrue(Long id);
}
