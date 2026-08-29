package com.biopet.controller;

import com.biopet.facturacion.dto.EstadoRequest;
import com.biopet.facturacion.dto.TarifaImpuestoRequest;
import com.biopet.facturacion.dto.TarifaImpuestoResponse;
import com.biopet.facturacion.service.TarifaImpuestoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Configuracion de tarifas de impuesto (Fase 8B). Solo lectura para ADMIN
 * (todo el historico) y AUXILIAR (solo activas); ningun otro rol tiene una
 * necesidad clara de verlas. Escritura exclusiva de ADMIN: {@code POST} abre
 * una vigencia nueva (nunca sobrescribe una historica, ver el javadoc de
 * {@link TarifaImpuestoService}), {@code PATCH /estado} es baja logica pura.
 * No hay {@code PUT}: no existe una edicion general segura de una fila
 * historica.
 */
@RestController
@RequestMapping("/api/facturacion/tarifas")
public class TarifaImpuestoController {

    private final TarifaImpuestoService tarifaImpuestoService;

    public TarifaImpuestoController(TarifaImpuestoService tarifaImpuestoService) {
        this.tarifaImpuestoService = tarifaImpuestoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public List<TarifaImpuestoResponse> listar(@AuthenticationPrincipal UserDetails userDetails) {
        return tarifaImpuestoService.listar(userDetails.getUsername());
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public TarifaImpuestoResponse buscar(@PathVariable Long id) {
        return tarifaImpuestoService.buscar(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TarifaImpuestoResponse> crear(@Valid @RequestBody TarifaImpuestoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tarifaImpuestoService.crear(request));
    }

    @PatchMapping("/{id:\\d+}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public TarifaImpuestoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest request) {
        return tarifaImpuestoService.cambiarEstado(id, request.activo());
    }
}
