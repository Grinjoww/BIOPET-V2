package com.biopet.facturacion.repository;

import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacturaDocumentoRepository extends JpaRepository<FacturaDocumento, Long> {

    /** Optional y no lista: hay como mucho uno de cada tipo por factura. */
    Optional<FacturaDocumento> findByFactura_IdAndTipo(Long facturaId, TipoDocumentoFactura tipo);

    List<FacturaDocumento> findAllByFactura_Id(Long facturaId);
}
