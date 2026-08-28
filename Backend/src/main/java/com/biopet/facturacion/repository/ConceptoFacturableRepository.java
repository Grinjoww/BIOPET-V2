package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.TipoConceptoFacturable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
