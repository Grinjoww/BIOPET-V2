package com.biopet.repository;

import com.biopet.entity.Consulta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {
    Page<Consulta> findAllByActivoTrue(Pageable pageable);

    /** Para GET /api/consultas/mascota/{mascotaId} (ConsultaService.listarPorMascota). */
    Page<Consulta> findAllByMascotaIdAndActivoTrue(Long mascotaId, Pageable pageable);

    /** Para ROLE_DUENO: todas las consultas de todas SUS mascotas, sin importar cuál. */
    Page<Consulta> findAllByMascota_Duenio_IdAndActivoTrue(Long duenioId, Pageable pageable);

    Optional<Consulta> findByIdAndActivoTrue(Long id);

    /**
     * Listado real de /api/consultas con filtros server-side (auditoría de
     * usabilidad + corrección de visibilidad por rol, fase "demo local").
     * Mismo patrón que {@code CitaRepository.buscar} para mascotaId/
     * veterinarioId/duenioId y para desde/hasta (TIMESTAMPTZ, nunca null).
     *
     * <p>{@code patronTexto} NO sigue ese patrón a propósito: es SIEMPRE un
     * patrón LIKE real, nunca null -a diferencia de un id o un enum, un
     * parámetro NULL dentro de {@code lower(?)} hace que Postgres (a
     * diferencia de H2) no pueda resolver el tipo del parámetro y falle con
     * "function lower(bytea) does not exist" en tiempo de ejecución (bug real
     * encontrado al probar contra biopet_db_1m_v2, no contra H2 -el mismo
     * tipo de discrepancia que el de CitaRepository.buscar con TIMESTAMPTZ-).
     * El Service siempre pasa {@code "%"} cuando no hay texto que buscar
     * -sigue casando con todo, sin necesitar "is null or" aquí-. Busca en
     * motivo Y diagnóstico -los dos campos con más probabilidad de contener
     * el texto que alguien recuerda de una consulta ("chequeo", "otitis")-.
     */
    @Query("""
            select c from Consulta c
            where c.activo = true
              and (:mascotaId is null or c.mascota.id = :mascotaId)
              and (:veterinarioId is null or c.veterinario.id = :veterinarioId)
              and (:duenioId is null or c.mascota.duenio.id = :duenioId)
              and (lower(c.motivo) like lower(:patronTexto)
                   or lower(c.diagnostico) like lower(:patronTexto))
              and c.fechaConsulta >= :desde
              and c.fechaConsulta <= :hasta
            """)
    Page<Consulta> buscar(@Param("mascotaId") Long mascotaId,
                           @Param("veterinarioId") Long veterinarioId,
                           @Param("duenioId") Long duenioId,
                           @Param("patronTexto") String patronTexto,
                           @Param("desde") Instant desde,
                           @Param("hasta") Instant hasta,
                           Pageable pageable);
}
