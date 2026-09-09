package com.biopet.controller;

import com.biopet.dto.UsuarioRequest;
import com.biopet.dto.UsuarioResponse;
import com.biopet.dto.UsuarioSeleccionableResponse;
import com.biopet.entity.Rol;
import com.biopet.service.AuthService;
import com.biopet.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Filtros opcionales (auditoría de usabilidad con datos masivos, fase
     * "demo local": con 2.002 cuentas hacía falta búsqueda): {@code q}
     * (nombre/email), {@code rol}, {@code estado} ("activo" | "inactivo" |
     * "todos"; por defecto "activo" -mismo comportamiento de siempre si el
     * cliente no manda nada-).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UsuarioResponse> listar(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Rol rol,
                                         @RequestParam(required = false, defaultValue = "activo") String estado,
                                         Pageable pageable) {
        boolean sinFiltros = (q == null || q.isBlank()) && rol == null && "activo".equalsIgnoreCase(estado);
        if (sinFiltros) {
            return usuarioService.listar(pageable);
        }
        Boolean activo = switch (estado.toLowerCase()) {
            case "activo" -> Boolean.TRUE;
            case "inactivo" -> Boolean.FALSE;
            default -> null; // "todos" (o cualquier otro valor): sin filtrar por estado
        };
        return usuarioService.buscar(pageable, q, rol, activo);
    }

    /**
     * Selector BUSCABLE para poblar el campo "dueño" en formularios de
     * mascota/factura (duenioId/usuarioId). No es el CRUD administrativo: no
     * expone password/passwordHash/activo ni ningún otro rol distinto de
     * ROLE_DUENO. ROLE_DUENO no lo necesita (nunca elige un dueño distinto de
     * sí mismo) y recibe 403.
     *
     * <p>{@code q} es OPCIONAL a propósito (auditoría de usabilidad con
     * datos masivos: ~1.800 dueños en la base de 1M ya no caben en un
     * {@code <select>} cargado de una vez): sin él, sigue paginando -nunca
     * todos de golpe-, solo sin filtrar por texto. {@code size} por defecto
     * 20, igual que el resto de los buscadores de este mismo cambio.
     */
    @GetMapping("/duenios")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public Page<UsuarioSeleccionableResponse> duenios(@RequestParam(required = false) String q,
                                                        @PageableDefault(size = 20) Pageable pageable) {
        return usuarioService.listarDuenios(q, pageable);
    }

    /**
     * Selector BUSCABLE para poblar el campo "veterinario" en formularios de
     * cita/consulta/vacuna (veterinarioId). Mismas restricciones, misma
     * audiencia y mismo criterio de paginación/búsqueda que /duenios.
     */
    @GetMapping("/veterinarios")
    @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')")
    public Page<UsuarioSeleccionableResponse> veterinarios(@RequestParam(required = false) String q,
                                                             @PageableDefault(size = 20) Pageable pageable) {
        return usuarioService.listarVeterinarios(q, pageable);
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
