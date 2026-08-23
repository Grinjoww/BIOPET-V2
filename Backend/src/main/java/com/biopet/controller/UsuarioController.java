package com.biopet.controller;

import com.biopet.dto.UsuarioRequest;
import com.biopet.dto.UsuarioResponse;
import com.biopet.dto.UsuarioSeleccionableResponse;
import com.biopet.service.AuthService;
import com.biopet.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final AuthService authService;
    private final UsuarioService usuarioService;

    public UsuarioController(AuthService authService, UsuarioService usuarioService) {
        this.authService = authService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal UserDetails userDetails) {
        return authService.perfil(userDetails.getUsername());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UsuarioResponse> listar(Pageable pageable) {
        return usuarioService.listar(pageable);
    }

    /**
     * Selector de solo lectura para poblar el campo "dueño" en formularios de
     * mascota (MascotaRequest.duenioId). No es el CRUD administrativo: no
     * expone password/passwordHash/activo ni ningún otro rol distinto de
     * ROLE_DUENO. ROLE_DUENO no lo necesita (nunca crea mascotas) y recibe 403.
     */
    @GetMapping("/duenios")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public List<UsuarioSeleccionableResponse> duenios() {
        return usuarioService.listarDuenios();
    }

    /**
     * Selector de solo lectura para poblar el campo "veterinario" en
     * formularios de cita/consulta/vacuna (veterinarioId). Mismas
     * restricciones y misma audiencia que /duenios.
     */
    @GetMapping("/veterinarios")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public List<UsuarioSeleccionableResponse> veterinarios() {
        return usuarioService.listarVeterinarios();
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResponse buscar(@PathVariable Long id) {
        return usuarioService.buscar(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(request));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest request,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        return usuarioService.actualizar(id, request, userDetails.getUsername());
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        usuarioService.eliminar(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
