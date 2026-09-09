package com.biopet.controller;

import com.biopet.dto.VacunaRequest;
import com.biopet.dto.VacunaResponse;
import com.biopet.service.VacunaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/vacunas")
public class VacunaController {
    private final VacunaService vacunaService;

    public VacunaController(VacunaService vacunaService) {
        this.vacunaService = vacunaService;
    }

    /**
     * Todos los filtros son opcionales (auditoría de usabilidad + corrección
     * de visibilidad por rol, fase "demo local").
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public Page<VacunaResponse> listar(@RequestParam(required = false) Long mascotaId,
                                        @RequestParam(required = false) Long veterinarioId,
                                        @RequestParam(required = false) String tipo,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                        Pageable pageable,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        boolean sinFiltros = mascotaId == null && veterinarioId == null && (tipo == null || tipo.isBlank()) && desde == null && hasta == null;
        if (sinFiltros) {
            return vacunaService.listar(pageable, userDetails.getUsername());
        }
        return vacunaService.buscar(pageable, userDetails.getUsername(), mascotaId, veterinarioId, tipo, desde, hasta);
    }

    @GetMapping("/mascota/{mascotaId:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public Page<VacunaResponse> listarPorMascota(@PathVariable Long mascotaId, Pageable pageable,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        return vacunaService.listarPorMascota(mascotaId, pageable, userDetails.getUsername());
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public VacunaResponse buscar(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        return vacunaService.buscar(id, userDetails.getUsername());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public ResponseEntity<VacunaResponse> crear(@Valid @RequestBody VacunaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vacunaService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public VacunaResponse actualizar(@PathVariable Long id, @Valid @RequestBody VacunaRequest request,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        return vacunaService.actualizar(id, request, userDetails.getUsername());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        vacunaService.eliminar(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
