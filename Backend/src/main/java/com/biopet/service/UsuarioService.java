package com.biopet.service;

import com.biopet.dto.UsuarioRequest;
import com.biopet.dto.UsuarioResponse;
import com.biopet.dto.UsuarioSeleccionableResponse;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.exception.EmailDuplicadoException;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD administrativo de usuarios (POST/PUT/DELETE /api/usuarios), restringido a
 * ROLE_ADMIN a nivel de {@code UsuarioController} (@PreAuthorize). No reemplaza ni
 * duplica {@code AuthService.registrar()}: aquella es el autoregistro público
 * (siempre ROLE_DUENO); este servicio permite a un administrador crear cuentas con
 * cualquier rol y gestionar cuentas existentes.
 */
@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(Pageable pageable) {
        return usuarioRepository.findAllByActivoTrue(pageable).map(this::toResponse);
    }

    /**
     * GET /api/usuarios con filtros server-side (nombre/email, rol, estado)
     * -auditoría de usabilidad con datos masivos, fase "demo local": con
     * 2.002 cuentas hacía falta búsqueda-. {@code activo} es null cuando el
     * cliente no elige un estado concreto -en ese caso se preserva el
     * comportamiento de siempre de {@link #listar}: solo cuentas activas-,
     * nunca "todas" implícitamente.
     */
    @Transactional(readOnly = true)
    public Page<UsuarioResponse> buscar(Pageable pageable, String q, Rol rol, Boolean activo) {
        // Nunca se pasa null a UsuarioRepository.buscarAdmin: "%" casa con
        // todo cuando no hay texto que filtrar (ver el javadoc del repositorio).
        String patronTexto = (q == null || q.isBlank()) ? "%" : "%" + q.trim() + "%";
        return usuarioRepository.buscarAdmin(activo, rol, patronTexto, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscar(Long id) {
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + id));
        return toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        String email = request.email().toLowerCase();
        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailDuplicadoException(email);
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria al crear un usuario.");
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.nombre())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .activo(true)
                .build();
        return toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioRequest request, String emailAutenticado) {
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + id));

        Usuario autenticado = usuarioRepository.findByEmailAndActivoTrue(emailAutenticado)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + emailAutenticado));
        if (autenticado.getId().equals(usuario.getId()) && request.rol() != usuario.getRol()) {
            throw new AccessDeniedException("No puede modificar su propio rol.");
        }

        String nuevoEmail = request.email().toLowerCase();
        usuarioRepository.findByEmail(nuevoEmail)
                .filter(otro -> !otro.getId().equals(usuario.getId()))
                .ifPresent(otro -> {
                    throw new EmailDuplicadoException(nuevoEmail);
                });

        usuario.setNombre(request.nombre());
        usuario.setEmail(nuevoEmail);
        usuario.setRol(request.rol());
        if (request.password() != null && !request.password().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return toResponse(usuarioRepository.save(usuario));
    }

    /**
     * Mismo patrón que actualizar(): un ADMIN no puede operar sobre su
     * propia cuenta de una forma que lo deje sin acceso. actualizar() ya
     * protege el cambio de rol propio; esto protege la autodesactivación
     * (única vía real hacia "cero administradores activos" a través de
     * esta API, ya que dar de baja a OTRO admin siempre deja al menos al
     * que ejecuta la operación activo).
     */
    @Transactional
    public void eliminar(Long id, String emailAutenticado) {
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + id));

        Usuario autenticado = usuarioRepository.findByEmailAndActivoTrue(emailAutenticado)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + emailAutenticado));
        if (autenticado.getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No puedes dar de baja tu propia cuenta.");
        }

        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    /**
     * Selector BUSCABLE de "dueño" al crear/editar una mascota o una factura
     * (duenioId/usuarioId). Accesible a ADMIN/VETERINARIO/AUXILIAR vía
     * UsuarioController; ROLE_DUENO no lo necesita porque nunca elige un
     * dueño distinto de sí mismo.
     *
     * <p>SIEMPRE paginado -con ~1.800 dueños en la base de 1M, cargar todos de
     * una vez en un {@code <select>} era exactamente el problema de usabilidad
     * que esto corrige (auditoría "usabilidad con datos masivos")-. {@code q}
     * es opcional: sin él, se sigue paginando (nunca las ~1.800 filas de
     * golpe), solo que sin filtrar por texto -el frontend nunca lo llama sin
     * {@code q} salvo para mostrar el estado inicial vacío del buscador.
     */
    @Transactional(readOnly = true)
    public Page<UsuarioSeleccionableResponse> listarDuenios(String q, Pageable pageable) {
        return usuarioRepository.buscarPorRolActivo(Rol.ROLE_DUENO, patronDeTexto(q), pageable)
                .map(this::toSeleccionableResponse);
    }

    /**
     * Selector BUSCABLE de "veterinario" al crear/editar una cita, consulta o
     * vacuna (veterinarioId). Mismo criterio de paginación que
     * {@link #listarDuenios}.
     */
    @Transactional(readOnly = true)
    public Page<UsuarioSeleccionableResponse> listarVeterinarios(String q, Pageable pageable) {
        return usuarioRepository.buscarPorRolActivo(Rol.ROLE_VETERINARIO, patronDeTexto(q), pageable)
                .map(this::toSeleccionableResponse);
    }

    /**
     * Nunca se pasa null a un {@code lower(:patron)} de JPQL -ver el
     * javadoc de UsuarioRepository.buscarPorRolActivo-: "%" casa con todo
     * cuando no hay texto que filtrar.
     */
    private static String patronDeTexto(String q) {
        return (q == null || q.isBlank()) ? "%" : "%" + q.trim() + "%";
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(), usuario.isActivo());
    }

    private UsuarioSeleccionableResponse toSeleccionableResponse(Usuario usuario) {
        return new UsuarioSeleccionableResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol());
    }
}
