package com.biopet.repository;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByEmailAndActivoTrue(String email);
    boolean existsByEmail(String email);
    Page<Usuario> findAllByActivoTrue(Pageable pageable);
    Optional<Usuario> findByIdAndActivoTrue(Long id);

    /**
     * Para los selectores buscables de "dueño"/"veterinario" (ver
     * UsuarioService.listarDuenios()/listarVeterinarios()). El {@code Rol} lo
     * fija siempre el Service con una constante; nunca debe derivarse de un
     * parámetro controlado por el cliente. {@code q} es opcional para quien
     * llama al Service (sin él, se pagina igual -nunca se cargan los ~2.000
     * usuarios de una vez-, solo sin filtrar por texto).
     *
     * <p>{@code patronTexto} en cambio NUNCA es null aquí: BUG REAL
     * encontrado al probar este mismo endpoint contra biopet_db_1m_v2 (no
     * contra H2, que lo toleraba) -un parámetro NULL dentro de
     * {@code lower(?)} hace que Postgres no pueda resolver su tipo y falla
     * en tiempo de ejecución con "function lower(bytea) does not exist".
     * Mismo tipo de discrepancia H2-vs-Postgres que el de
     * CitaRepository.buscar con TIMESTAMPTZ. El Service siempre traduce
     * "sin q" a {@code "%"} (casa con todo) antes de llegar aquí.
     *
     * <p>Usuarios es una tabla pequeña (2.002 filas en la base de 1M):
     * {@code LOWER(...) LIKE} sin índice de texto es intencional aquí -medido
     * contra biopet_db_1m_v2 antes de escribir esta consulta, ver
     * scripts/db/evidencia-optimizacion/-, no hace falta un índice nuevo para
     * que sea rápida.
     */
    @Query("""
            select u from Usuario u
            where u.rol = :rol and u.activo = true
              and (lower(u.nombre) like lower(:patronTexto)
                   or lower(u.email) like lower(:patronTexto))
            order by u.nombre asc
            """)
    Page<Usuario> buscarPorRolActivo(@Param("rol") Rol rol, @Param("patronTexto") String patronTexto, Pageable pageable);

    /**
     * Listado ADMINISTRATIVO real de /api/usuarios (nombre/email/rol/estado)
     * -auditoría de usabilidad con datos masivos, fase "demo local": con
     * 2.002 cuentas hacía falta búsqueda-. Distinto de
     * {@link #buscarPorRolActivo}: ese es el selector de solo
     * dueños/veterinarios activos; este es el CRUD completo, con el mismo
     * criterio "(:param is null or ...)" para activo/rol y, a diferencia del
     * resto, SÍ puede mostrar cuentas inactivas -es la única pantalla donde
     * eso tiene sentido, y solo para ADMIN-. {@code patronTexto} sigue el
     * mismo criterio "nunca null" que {@link #buscarPorRolActivo} -ver su
     * javadoc para el porqué-.
     */
    @Query("""
            select u from Usuario u
            where (:activo is null or u.activo = :activo)
              and (:rol is null or u.rol = :rol)
              and (lower(u.nombre) like lower(:patronTexto)
                   or lower(u.email) like lower(:patronTexto))
            """)
    Page<Usuario> buscarAdmin(@Param("activo") Boolean activo, @Param("rol") Rol rol,
                              @Param("patronTexto") String patronTexto, Pageable pageable);
}
