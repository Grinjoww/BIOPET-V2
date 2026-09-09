package com.biopet.repository;

import com.biopet.entity.Mascota;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MascotaRepository extends JpaRepository<Mascota, Long> {
    Page<Mascota> findAllByActivoTrue(Pageable pageable);
    Page<Mascota> findAllByDuenioIdAndActivoTrue(Long duenioId, Pageable pageable);
    Optional<Mascota> findByIdAndActivoTrue(Long id);

    /**
     * Selector BUSCABLE de "mascota" al crear/editar una cita, consulta,
     * vacuna o factura (mascotaId). {@code q} es opcional -mismo patron
     * "(:q is null or ...)" que UsuarioRepository.buscarPorRolActivo-: sin
     * él, sigue paginando (nunca las ~10.000 filas de golpe). Busca por
     * nombre de la mascota O nombre/email de su dueño -exactamente lo que ya
     * se mostraba en el selector antiguo ("nombre — expediente —
     * duenioNombre")-, y ademas por id exacto si {@code q} es puramente
     * numerico (para quien ya conoce el id/expediente).
     *
     * <p>10.000 filas es una tabla pequena para Postgres: LOWER(...) LIKE sin
     * indice de texto se midio contra biopet_db_1m_v2 antes de escribir esta
     * consulta (ver scripts/db/evidencia-optimizacion/) y es rapida sin
     * necesitar un indice nuevo.
     */
    @Query("""
            select m from Mascota m
            where m.activo = true
              and (:q is null
                   or lower(m.nombre) like lower(concat('%', :q, '%'))
                   or lower(m.duenio.nombre) like lower(concat('%', :q, '%'))
                   or lower(m.duenio.email) like lower(concat('%', :q, '%'))
                   or (:qId is not null and m.id = :qId))
            order by m.nombre asc
            """)
    Page<Mascota> buscarActivas(@Param("q") String q, @Param("qId") Long qId, Pageable pageable);

    /** Misma búsqueda que {@link #buscarActivas}, acotada a las mascotas de UN dueño (rol ROLE_DUENO). */
    @Query("""
            select m from Mascota m
            where m.activo = true and m.duenio.id = :duenioId
              and (:q is null
                   or lower(m.nombre) like lower(concat('%', :q, '%'))
                   or (:qId is not null and m.id = :qId))
            order by m.nombre asc
            """)
    Page<Mascota> buscarActivasPorDuenio(@Param("duenioId") Long duenioId, @Param("q") String q,
                                          @Param("qId") Long qId, Pageable pageable);
}
