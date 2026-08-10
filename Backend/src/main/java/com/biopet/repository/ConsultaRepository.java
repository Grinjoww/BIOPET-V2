package com.biopet.repository;

import com.biopet.entity.Consulta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {
    Page<Consulta> findAllByActivoTrue(Pageable pageable);
    Page<Consulta> findAllByMascotaIdAndActivoTrue(Long mascotaId, Pageable pageable);
    Optional<Consulta> findByIdAndActivoTrue(Long id);
}