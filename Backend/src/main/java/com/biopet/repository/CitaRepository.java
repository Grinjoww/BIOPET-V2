package com.biopet.repository;

import com.biopet.entity.Cita;
import com.biopet.entity.EstadoCita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CitaRepository extends JpaRepository<Cita, Long> {
    Page<Cita> findAllByActivoTrue(Pageable pageable);
    Page<Cita> findAllByMascota_Duenio_IdAndActivoTrue(Long duenioId, Pageable pageable);

    /** Para GET /api/citas/mascota/{mascotaId} (CitaService.listarPorMascota). */
    Page<Cita> findAllByMascotaIdAndActivoTrue(Long mascotaId, Pageable pageable);

    Optional<Cita> findByIdAndActivoTrue(Long id);

    /**
     * Listado real de /api/citas con filtros server-side (auditoría de
     * usabilidad + corrección de visibilidad por rol, fase "demo local").
     * Todos los parámetros salvo desde/hasta son opcionales -patrón
     * "(:param is null or ...)" ya usado en MascotaRepository.buscarActivas
     * y UsuarioRepository.buscarPorRolActivo-.
     *
     * <p>{@code veterinarioId} y {@code duenioId} cumplen DOS roles a la
     * vez: cuando el usuario elige un filtro en el formulario Y cuando el
     * backend fuerza el alcance por rol (CitaService.buscar calcula el
     * valor EFECTIVO antes de llamar aquí -un ROLE_VETERINARIO nunca puede
     * pedir ver citas de otro veterinario, sin importar qué envíe el
     * cliente-).
     *
     * <p>{@code desde}/{@code hasta} NO usan el patrón "is null or" a
     * propósito: con una columna TIMESTAMPTZ, Postgres (a diferencia de H2)
     * falla al inferir el tipo de un parámetro NULL ligado dos veces en la
     * misma consulta (ver AuditoriaEventoRepository.buscar). El Service
     * siempre pasa límites reales (Instant.EPOCH / año 9999 si no se pidió
     * rango), nunca null.
     */
    @Query("""
            select c from Cita c
            where c.activo = true
              and (:mascotaId is null or c.mascota.id = :mascotaId)
              and (:veterinarioId is null or c.veterinario.id = :veterinarioId)
              and (:duenioId is null or c.mascota.duenio.id = :duenioId)
              and (:estado is null or c.estado = :estado)
              and c.fechaHora >= :desde
              and c.fechaHora <= :hasta
            """)
    Page<Cita> buscar(@Param("mascotaId") Long mascotaId,
                       @Param("veterinarioId") Long veterinarioId,
                       @Param("duenioId") Long duenioId,
                       @Param("estado") EstadoCita estado,
                       @Param("desde") Instant desde,
                       @Param("hasta") Instant hasta,
                       Pageable pageable);
}
