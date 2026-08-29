package com.biopet.controller;

import com.biopet.facturacion.dto.ActualizarPuntoEmisionRequest;
import com.biopet.facturacion.dto.EstadoRequest;
import com.biopet.facturacion.dto.PuntoEmisionRequest;
import com.biopet.facturacion.dto.PuntoEmisionResponse;
import com.biopet.facturacion.service.PuntoEmisionService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogo de puntos de emision (Fase 8B). ADMIN administra cualquiera
 * (activo o no); AUXILIAR solo lee los ACTIVOS -es quien elige
 * {@code puntoEmisionId} al preparar una emision-. VETERINARIO/DUENO no tienen
 * acceso: no participan en la seleccion del punto de emision.
 */
@RestController
@RequestMapping("/api/facturacion/puntos-emision")
public class PuntoEmisionController {

    private final PuntoEmisionService puntoEmisionService;

    public PuntoEmisionController(PuntoEmisionService puntoEmisionService) {
        this.puntoEmisionService = puntoEmisionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public List<PuntoEmisionResponse> listar(@AuthenticationPrincipal UserDetails userDetails) {
        return puntoEmisionService.listar(userDetails.getUsername());
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public PuntoEmisionResponse buscar(@PathVariable Long id) {
        return puntoEmisionService.buscar(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PuntoEmisionResponse> crear(@Valid @RequestBody PuntoEmisionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(puntoEmisionService.crear(request));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    public PuntoEmisionResponse actualizar(@PathVariable Long id,
                                           @Valid @RequestBody ActualizarPuntoEmisionRequest request) {
        return puntoEmisionService.actualizar(id, request);
    }

    @PatchMapping("/{id:\\d+}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public PuntoEmisionResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest request) {
        return puntoEmisionService.cambiarEstado(id, request.activo());
    }
}
