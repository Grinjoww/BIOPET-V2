package com.biopet.service;

import com.biopet.dto.UsuarioRequest;
import com.biopet.dto.UsuarioResponse;
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

    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + id));
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(), usuario.isActivo());
    }
}
