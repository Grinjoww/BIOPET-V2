package com.biopet.controller;

import com.biopet.dto.ConsultaRequest;
import com.biopet.dto.ConsultaResponse;
import com.biopet.service.ConsultaService;
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
@RequestMapping("/api/consultas")
public class ConsultaController {
    private final ConsultaService consultaService;

    public ConsultaController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public Page<ConsultaResponse> listar(Pageable pageable, @AuthenticationPrincipal UserDetails userDetails) {
        return consultaService.listar(pageable, userDetails.getUsername());
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public ConsultaResponse buscar(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        return consultaService.buscar(id, userDetails.getUsername());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public ResponseEntity<ConsultaResponse> crear(@Valid @RequestBody ConsultaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consultaService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public ConsultaResponse actualizar(@PathVariable Long id, @Valid @RequestBody ConsultaRequest request,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        return consultaService.actualizar(id, request, userDetails.getUsername());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        consultaService.eliminar(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}