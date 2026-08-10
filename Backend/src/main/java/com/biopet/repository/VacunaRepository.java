package com.biopet.repository;

import com.biopet.entity.Vacuna;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VacunaRepository extends JpaRepository<Vacuna, Long> {
    Page<Vacuna> findAllByActivoTrue(Pageable pageable);

    Page<Vacuna> findAllByMascotaIdAndActivoTrue(Long mascotaId, Pageable pageable);

    /** Para ROLE_DUENO: todas las vacunas de todas SUS mascotas, sin importar cuál. */
    Page<Vacuna> findAllByMascota_Duenio_IdAndActivoTrue(Long duenioId, Pageable pageable);

    Optional<Vacuna> findByIdAndActivoTrue(Long id);
}
