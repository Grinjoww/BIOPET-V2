package com.biopet.controller;

import com.biopet.facturacion.dto.DatosFacturacionRequest;
import com.biopet.facturacion.dto.DatosFacturacionResponse;
import com.biopet.facturacion.service.DatosFacturacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Identidades tributarias (datos de facturacion) de un usuario. Cierra el
 * hueco operativo que la Fase 8A dejo abierto: hasta ahora solo se podian
 * SELECCIONAR datos ya existentes al armar un borrador
 * ({@code FacturaBorradorService#seleccionarComprador}); aqui se listan,
 * crean, editan y gestionan.
 *
 * <h2>Permisos</h2>
 * <ul>
 *   <li>ADMIN/AUXILIAR: cualquier {@code usuarioId}.</li>
 *   <li>DUENO: SOLO el suyo propio -{@code @PreAuthorize} no basta para
 *       expresar eso; el ownership real por fila se comprueba en
 *       {@link DatosFacturacionService}, que lanza 403 si el
 *       {@code usuarioId} del path no es el del autenticado-.</li>
 *   <li>VETERINARIO: sin acceso. No necesita los datos fiscales completos de
 *       un comprador para su trabajo clinico.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/usuarios/{usuarioId}/datos-facturacion")
public class DatosFacturacionController {

    private final DatosFacturacionService datosFacturacionService;

    public DatosFacturacionController(DatosFacturacionService datosFacturacionService) {
        this.datosFacturacionService = datosFacturacionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','DUENO')")
    public List<DatosFacturacionResponse> listar(@PathVariable Long usuarioId,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        return datosFacturacionService.listar(usuarioId, userDetails.getUsername());
    }

    /** Ruta literal: nunca colisiona con {@code /{id:\d+}}, que solo acepta digitos. */
    @GetMapping("/predeterminado")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','DUENO')")
    public DatosFacturacionResponse predeterminado(@PathVariable Long usuarioId,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        return datosFacturacionService.obtenerPredeterminado(usuarioId, userDetails.getUsername());
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','DUENO')")
    public DatosFacturacionResponse buscar(@PathVariable Long usuarioId, @PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        return datosFacturacionService.buscar(usuarioId, id, userDetails.getUsername());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','DUENO')")
    public ResponseEntity<DatosFacturacionResponse> crear(@PathVariable Long usuarioId,
                                                          @Valid @RequestBody DatosFacturacionRequest request,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        DatosFacturacionResponse creado = datosFacturacionService.crear(usuarioId, request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','DUENO')")
    public DatosFacturacionResponse actualizar(@PathVariable Long usuarioId, @PathVariable Long id,
                                               @Valid @RequestBody DatosFacturacionRequest request,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        return datosFacturacionService.actualizar(usuarioId, id, request, userDetails.getUsername());
    }

    @PatchMapping("/{id:\\d+}/predeterminado")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','DUENO')")
    public DatosFacturacionResponse marcarPredeterminado(@PathVariable Long usuarioId, @PathVariable Long id,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        return datosFacturacionService.marcarPredeterminado(usuarioId, id, userDetails.getUsername());
    }

    /** Baja logica ({@code activo = false}), nunca DELETE fisico: ver {@code DatosFacturacionService#desactivar}. */
    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','DUENO')")
    public ResponseEntity<Void> desactivar(@PathVariable Long usuarioId, @PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        datosFacturacionService.desactivar(usuarioId, id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
