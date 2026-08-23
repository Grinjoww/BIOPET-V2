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

import java.util.List;

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
     * Solo lectura, para poblar el selector de "dueño" al crear/editar una
     * mascota (MascotaRequest.duenioId). Accesible a ADMIN/VETERINARIO/AUXILIAR
     * vía UsuarioController; ROLE_DUENO no lo necesita porque nunca crea
     * mascotas.
     */
    @Transactional(readOnly = true)
    public List<UsuarioSeleccionableResponse> listarDuenios() {
        return usuarioRepository.findAllByRolAndActivoTrueOrderByNombreAsc(Rol.ROLE_DUENO).stream()
                .map(this::toSeleccionableResponse)
                .toList();
    }

    /**
     * Solo lectura, para poblar el selector de "veterinario" al crear/editar
     * una cita, consulta o vacuna (veterinarioId).
     */
    @Transactional(readOnly = true)
    public List<UsuarioSeleccionableResponse> listarVeterinarios() {
        return usuarioRepository.findAllByRolAndActivoTrueOrderByNombreAsc(Rol.ROLE_VETERINARIO).stream()
                .map(this::toSeleccionableResponse)
                .toList();
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(), usuario.isActivo());
    }

    private UsuarioSeleccionableResponse toSeleccionableResponse(Usuario usuario) {
        return new UsuarioSeleccionableResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol());
    }
}
