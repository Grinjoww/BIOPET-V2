package com.biopet.facturacion.service;

import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.dto.ConceptoFacturableRequest;
import com.biopet.facturacion.dto.ConceptoFacturableResponse;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.TipoConceptoFacturable;
import com.biopet.facturacion.repository.ConceptoFacturableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catalogo de conceptos facturables para la Fase 8B: lectura filtrable para
 * ADMIN/AUXILIAR/VETERINARIO y administracion (alta, edicion, baja logica)
 * exclusiva de ADMIN -la matriz de permisos vive en
 * {@link com.biopet.controller.ConceptoFacturableController}, aqui solo la
 * logica de negocio-.
 *
 * <p>Nunca hay DELETE fisico: un concepto ya usado en una factura sigue
 * existiendo como trazabilidad de {@code FacturaDetalle.conceptoFacturable}
 * (relacion nullable, ver su javadoc), y la baja SIEMPRE es {@code activo =
 * false} via {@link #cambiarEstado}.
 *
 * <p>Editar {@code codigo}, {@code descripcion}, {@code precioUnitario} o el
 * par de impuesto es seguro para facturas ya emitidas: {@code FacturaDetalle}
 * congela su propio snapshot al facturar y nunca vuelve a leer esta tabla.
 */
@Service
public class ConceptoFacturableService {

    private final ConceptoFacturableRepository conceptoFacturableRepository;

    public ConceptoFacturableService(ConceptoFacturableRepository conceptoFacturableRepository) {
        this.conceptoFacturableRepository = conceptoFacturableRepository;
    }

    @Transactional(readOnly = true)
    public List<ConceptoFacturableResponse> listar(Boolean activo, TipoConceptoFacturable tipo) {
        return conceptoFacturableRepository.buscar(activo, tipo).stream()
                .map(ConceptoFacturableService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConceptoFacturableResponse buscar(Long id) {
        return toResponse(conceptoPorId(id));
    }

    @Transactional
    public ConceptoFacturableResponse crear(ConceptoFacturableRequest request) {
        ConceptoFacturable concepto = ConceptoFacturable.builder()
                .codigo(request.codigo().trim())
                .descripcion(request.descripcion().trim())
                .tipo(request.tipo())
                .precioUnitario(request.precioUnitario())
                .codigoImpuesto(request.codigoImpuesto())
                .codigoPorcentaje(request.codigoPorcentaje())
                .activo(true)
                .build();
        return toResponse(conceptoFacturableRepository.save(concepto));
    }

    @Transactional
    public ConceptoFacturableResponse actualizar(Long id, ConceptoFacturableRequest request) {
        ConceptoFacturable concepto = conceptoPorId(id);
        concepto.setCodigo(request.codigo().trim());
        concepto.setDescripcion(request.descripcion().trim());
        concepto.setTipo(request.tipo());
        concepto.setPrecioUnitario(request.precioUnitario());
        concepto.setCodigoImpuesto(request.codigoImpuesto());
        concepto.setCodigoPorcentaje(request.codigoPorcentaje());
        return toResponse(conceptoFacturableRepository.save(concepto));
    }

    /** Alta/baja logica exclusivamente: nunca toca ningun otro campo. */
    @Transactional
    public ConceptoFacturableResponse cambiarEstado(Long id, boolean activo) {
        ConceptoFacturable concepto = conceptoPorId(id);
        concepto.setActivo(activo);
        return toResponse(conceptoFacturableRepository.save(concepto));
    }

    private ConceptoFacturable conceptoPorId(Long id) {
        return conceptoFacturableRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el concepto facturable con id " + id + "."));
    }

    private static ConceptoFacturableResponse toResponse(ConceptoFacturable c) {
        return new ConceptoFacturableResponse(
                c.getId(), c.getCodigo(), c.getDescripcion(), c.getTipo(), c.getPrecioUnitario(),
                c.getCodigoImpuesto(), c.getCodigoPorcentaje(), c.isActivo());
    }
}
