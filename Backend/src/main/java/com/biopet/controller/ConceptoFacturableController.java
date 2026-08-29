package com.biopet.controller;

import com.biopet.facturacion.dto.ConceptoFacturableRequest;
import com.biopet.facturacion.dto.ConceptoFacturableResponse;
import com.biopet.facturacion.dto.EstadoRequest;
import com.biopet.facturacion.entity.TipoConceptoFacturable;
import com.biopet.facturacion.service.ConceptoFacturableService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogo de conceptos facturables (Fase 8B). Lectura para ADMIN/AUXILIAR/
 * VETERINARIO (este ultimo solo para consulta clinica, nunca administracion);
 * alta, edicion y baja logica exclusivas de ADMIN. DUENO no tiene acceso: no
 * necesita administrar el catalogo, lo ve ya congelado en el detalle de sus
 * facturas.
 */
@RestController
@RequestMapping("/api/facturacion/conceptos")
public class ConceptoFacturableController {

    private final ConceptoFacturableService conceptoFacturableService;

    public ConceptoFacturableController(ConceptoFacturableService conceptoFacturableService) {
        this.conceptoFacturableService = conceptoFacturableService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','VETERINARIO')")
    public List<ConceptoFacturableResponse> listar(@RequestParam(required = false) Boolean activo,
                                                   @RequestParam(required = false) TipoConceptoFacturable tipo) {
        return conceptoFacturableService.listar(activo, tipo);
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','VETERINARIO')")
    public ConceptoFacturableResponse buscar(@PathVariable Long id) {
        return conceptoFacturableService.buscar(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConceptoFacturableResponse> crear(@Valid @RequestBody ConceptoFacturableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conceptoFacturableService.crear(request));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    public ConceptoFacturableResponse actualizar(@PathVariable Long id,
                                                 @Valid @RequestBody ConceptoFacturableRequest request) {
        return conceptoFacturableService.actualizar(id, request);
    }

    @PatchMapping("/{id:\\d+}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ConceptoFacturableResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest request) {
        return conceptoFacturableService.cambiarEstado(id, request.activo());
    }
}
