package com.biopet.repository;

import com.biopet.entity.Consulta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {
    Page<Consulta> findAllByActivoTrue(Pageable pageable);

    /** Para GET /api/consultas/mascota/{mascotaId} (ConsultaService.listarPorMascota). */
    Page<Consulta> findAllByMascotaIdAndActivoTrue(Long mascotaId, Pageable pageable);

    /** Para ROLE_DUENO: todas las consultas de todas SUS mascotas, sin importar cuál. */
    Page<Consulta> findAllByMascota_Duenio_IdAndActivoTrue(Long duenioId, Pageable pageable);

    Optional<Consulta> findByIdAndActivoTrue(Long id);
}