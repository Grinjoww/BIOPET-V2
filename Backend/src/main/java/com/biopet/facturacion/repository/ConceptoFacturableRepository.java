package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.TipoConceptoFacturable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConceptoFacturableRepository extends JpaRepository<ConceptoFacturable, Long> {

    /** El codigo solo es unico entre los activos; de ahi el AND del nombre. */
    Optional<ConceptoFacturable> findByCodigoAndActivoTrue(String codigo);

    /**
     * Concepto activo por id. Vacio tanto si no existe como si fue dado de
     * baja: para armar o emitir una linea, ambos casos son el mismo problema.
     */
    Optional<ConceptoFacturable> findByIdAndActivoTrue(Long id);

    List<ConceptoFacturable> findAllByTipoAndActivoTrue(TipoConceptoFacturable tipo);

    /**
     * Listado filtrable de la Fase 8B: {@code activo}/{@code tipo} son
     * opcionales de forma independiente (cualquiera de los dos, ninguno o
     * ambos), asi que se resuelve con UNA consulta de parametros nulables en
     * lugar de encadenar metodos derivados para cada combinacion.
     */
    @Query("select c from ConceptoFacturable c "
            + "where (:activo is null or c.activo = :activo) "
            + "and (:tipo is null or c.tipo = :tipo) "
            + "order by c.codigo asc")
    List<ConceptoFacturable> buscar(@Param("activo") Boolean activo, @Param("tipo") TipoConceptoFacturable tipo);
}
