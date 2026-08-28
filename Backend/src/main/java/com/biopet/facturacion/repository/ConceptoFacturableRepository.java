package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.TipoConceptoFacturable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConceptoFacturableRepository extends JpaRepository<ConceptoFacturable, Long> {

    /** El codigo solo es unico entre los activos; de ahi el AND del nombre. */
    Optional<ConceptoFacturable> findByCodigoAndActivoTrue(String codigo);

    List<ConceptoFacturable> findAllByTipoAndActivoTrue(TipoConceptoFacturable tipo);
}
