package com.biopet.controller;

import com.biopet.dto.CitaRequest;
import com.biopet.dto.CitaResponse;
import com.biopet.service.CitaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/citas")
public class CitaController {
    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public Page<CitaResponse> listar(Pageable pageable, @AuthenticationPrincipal UserDetails userDetails) {
        return citaService.listar(pageable, userDetails.getUsername());
    }

    /**
     * Para la pestaña "Citas" de la ficha de mascota. Es de solo lectura:
     * los mismos 4 roles que pueden leer /api/citas pueden leerlo, y no
     * cambia en nada quién puede crear/actualizar/eliminar citas.
     */
    @GetMapping("/mascota/{mascotaId:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public Page<CitaResponse> listarPorMascota(@PathVariable Long mascotaId, Pageable pageable,
                                                @AuthenticationPrincipal UserDetails userDetails) {
        return citaService.listarPorMascota(mascotaId, pageable, userDetails.getUsername());
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public CitaResponse buscar(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        return citaService.buscar(id, userDetails.getUsername());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public ResponseEntity<CitaResponse> crear(@Valid @RequestBody CitaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(citaService.crear(request));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','VETERINARIO')")
    public CitaResponse actualizar(@PathVariable Long id, @Valid @RequestBody CitaRequest request,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        return citaService.actualizar(id, request, userDetails.getUsername());
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        citaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
