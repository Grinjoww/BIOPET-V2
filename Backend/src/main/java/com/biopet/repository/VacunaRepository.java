package com.biopet.repository;

import com.biopet.entity.Vacuna;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface VacunaRepository extends JpaRepository<Vacuna, Long> {
    Page<Vacuna> findAllByActivoTrue(Pageable pageable);

    Page<Vacuna> findAllByMascotaIdAndActivoTrue(Long mascotaId, Pageable pageable);

    /** Para ROLE_DUENO: todas las vacunas de todas SUS mascotas, sin importar cuál. */
    Page<Vacuna> findAllByMascota_Duenio_IdAndActivoTrue(Long duenioId, Pageable pageable);

    Optional<Vacuna> findByIdAndActivoTrue(Long id);

    /**
     * Listado real de /api/vacunas con filtros server-side (auditoría de
     * usabilidad + corrección de visibilidad por rol, fase "demo local").
     * Mismo patrón que CitaRepository.buscar/ConsultaRepository.buscar.
     * {@code desde}/{@code hasta} filtran fechaAplicacion; aunque es un
     * DATE (no TIMESTAMPTZ), se aplica el mismo criterio de "nunca pasar
     * null" que en Cita/Consulta por consistencia y para no arriesgar el
     * mismo tipo de fallo con Postgres. {@code veterinarioId is null} deja
     * fuera las vacunas SIN veterinario asignado cuando se filtra por uno
     * concreto -correcto: si se pide "las vacunas del Dr. X", una sin
     * veterinario asignado no es de él-.
     *
     * <p>{@code patronTipo} (Vacuna.tipo es texto libre, no un enum) NUNCA
     * es null -a diferencia de un id, un parámetro NULL dentro de
     * {@code lower(?)} hace que Postgres (a diferencia de H2) falle con
     * "function lower(bytea) does not exist" en tiempo de ejecución (bug
     * real encontrado al probar contra biopet_db_1m_v2; ver el javadoc
     * idéntico en ConsultaRepository.buscar). El Service siempre pasa
     * {@code "%"} cuando no hay tipo que filtrar.
     */
    @Query("""
            select v from Vacuna v
            where v.activo = true
              and (:mascotaId is null or v.mascota.id = :mascotaId)
              and (:veterinarioId is null or v.veterinario.id = :veterinarioId)
              and (:duenioId is null or v.mascota.duenio.id = :duenioId)
              and lower(v.tipo) like lower(:patronTipo)
              and v.fechaAplicacion >= :desde
              and v.fechaAplicacion <= :hasta
            """)
    Page<Vacuna> buscar(@Param("mascotaId") Long mascotaId,
                         @Param("veterinarioId") Long veterinarioId,
                         @Param("duenioId") Long duenioId,
                         @Param("patronTipo") String patronTipo,
                         @Param("desde") LocalDate desde,
                         @Param("hasta") LocalDate hasta,
                         Pageable pageable);
}
