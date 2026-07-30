package com.biopet.controller;

import com.biopet.dto.MascotaRequest;
import com.biopet.dto.MascotaResponse;
import com.biopet.dto.ResumenEspecieResponse;
import com.biopet.service.MascotaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mascotas")
public class MascotaController {
    private final MascotaService mascotaService;

    public MascotaController(MascotaService mascotaService) {
        this.mascotaService = mascotaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public Page<MascotaResponse> listar(Pageable pageable, @AuthenticationPrincipal UserDetails userDetails) {
        return mascotaService.listar(pageable, userDetails.getUsername());
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public MascotaResponse buscar(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        return mascotaService.buscar(id, userDetails.getUsername());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public ResponseEntity<MascotaResponse> crear(@Valid @RequestBody MascotaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mascotaService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public MascotaResponse actualizar(@PathVariable Long id, @Valid @RequestBody MascotaRequest request,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        return mascotaService.actualizar(id, request, userDetails.getUsername());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        mascotaService.eliminar(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/resumen-especies")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public List<ResumenEspecieResponse> resumenPorEspecies(
            @RequestParam(required = false) Long duenioId,
            Authentication authentication) {
        return mascotaService.resumenPorEspecie(duenioId, authentication.getName());
    }
}