package com.biopet.controller;

import com.biopet.dto.ConsultaRequest;
import com.biopet.dto.ConsultaResponse;
import com.biopet.service.ConsultaService;
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

import java.time.Instant;

@RestController
@RequestMapping("/api/consultas")
public class ConsultaController {
    private final ConsultaService consultaService;

    public ConsultaController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    /**
     * Todos los filtros son opcionales (auditoría de usabilidad + corrección
     * de visibilidad por rol, fase "demo local"): sin ninguno, usa
     * consultaService.listar (con cache), igual que antes -ya con el
     * alcance por rol correcto-.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public Page<ConsultaResponse> listar(@RequestParam(required = false) Long mascotaId,
                                          @RequestParam(required = false) Long veterinarioId,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
                                          Pageable pageable,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        boolean sinFiltros = mascotaId == null && veterinarioId == null && (q == null || q.isBlank()) && desde == null && hasta == null;
        if (sinFiltros) {
            return consultaService.listar(pageable, userDetails.getUsername());
        }
        return consultaService.buscar(pageable, userDetails.getUsername(), mascotaId, veterinarioId, q, desde, hasta);
    }

    /**
     * Para la pestaña "Consultas" de la ficha de mascota. La restricción
     * `\\d+` en /{id} y el path fijo "/mascota/{mascotaId}" no colisionan:
     * Spring MVC prioriza la coincidencia exacta de segmento de ruta sobre
     * un patrón con variable, así que "/mascota/7" nunca se interpreta
     * como /{id} con id="mascota".
     */
    @GetMapping("/mascota/{mascotaId:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')")
    public Page<ConsultaResponse> listarPorMascota(@PathVariable Long mascotaId, Pageable pageable,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        return consultaService.listarPorMascota(mascotaId, pageable, userDetails.getUsername());
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