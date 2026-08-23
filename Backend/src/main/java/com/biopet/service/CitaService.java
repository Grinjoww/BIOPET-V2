package com.biopet.service;

import com.biopet.dto.CitaRequest;
import com.biopet.dto.CitaResponse;
import com.biopet.entity.Cita;
import com.biopet.entity.EstadoCita;
import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.repository.CitaRepository;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de citas (agendamiento previo de atención veterinaria). No reemplaza ni
 * duplica el futuro módulo de "Consulta" (registro clínico posterior), que
 * pertenece a otro integrante del equipo.
 * <p>
 * Reglas de acceso (aplicadas aquí porque dependen de datos, no solo del rol;
 * el control por rol "puro" ya vive en {@code CitaController} vía @PreAuthorize):
 * <ul>
 *   <li>DUENO: solo lee citas de sus propias mascotas (igual que MascotaService).</li>
 *   <li>VETERINARIO: lee todas, pero solo puede actualizar las citas donde él
 *       es el veterinario asignado.</li>
 *   <li>ADMIN/AUXILIAR: sin restricciones adicionales de datos.</li>
 * </ul>
 */
@Service
public class CitaService {
    private final CitaRepository citaRepository;
    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;

    public CitaService(CitaRepository citaRepository, MascotaRepository mascotaRepository, UsuarioRepository usuarioRepository) {
        this.citaRepository = citaRepository;
        this.mascotaRepository = mascotaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public Page<CitaResponse> listar(Pageable pageable, String email) {
        Usuario usuario = usuarioActual(email);
        if (usuario.getRol() == Rol.ROLE_DUENO) {
            return citaRepository.findAllByMascota_Duenio_IdAndActivoTrue(usuario.getId(), pageable).map(this::toResponse);
        }
        return citaRepository.findAllByActivoTrue(pageable).map(this::toResponse);
    }

    /**
     * GET /api/citas/mascota/{mascotaId}. Solo lectura: usa
     * verificarAccesoLecturaMascota (misma regla que buscar()), nunca la
     * regla de escritura de actualizar() — este endpoint no amplía qué
     * citas puede modificar un VETERINARIO, solo cuáles puede leer.
     */
    @Transactional(readOnly = true)
    public Page<CitaResponse> listarPorMascota(Long mascotaId, Pageable pageable, String email) {
        Usuario usuario = usuarioActual(email);
        Mascota mascota = resolverMascota(mascotaId);
        verificarAccesoLecturaMascota(usuario, mascota);
        return citaRepository.findAllByMascotaIdAndActivoTrue(mascotaId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CitaResponse buscar(Long id, String email) {
        Usuario usuario = usuarioActual(email);
        Cita cita = citaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada: " + id));
        verificarAccesoLectura(usuario, cita);
        return toResponse(cita);
    }

    @Transactional
    public CitaResponse crear(CitaRequest request) {
        Mascota mascota = resolverMascota(request.mascotaId());
        Usuario veterinario = resolverVeterinario(request.veterinarioId());

        Cita cita = Cita.builder()
                .mascota(mascota)
                .veterinario(veterinario)
                .fechaHora(request.fechaHora())
                .estado(EstadoCita.PROGRAMADA)
                .motivo(request.motivo())
                .activo(true)
                .build();
        return toResponse(citaRepository.save(cita));
    }

    @Transactional
    public CitaResponse actualizar(Long id, CitaRequest request, String email) {
        Usuario usuario = usuarioActual(email);
        Cita cita = citaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada: " + id));
        verificarPermisoEscritura(usuario, cita);

        Mascota mascota = resolverMascota(request.mascotaId());
        Usuario veterinario = resolverVeterinario(request.veterinarioId());

        cita.setMascota(mascota);
        cita.setVeterinario(veterinario);
        cita.setFechaHora(request.fechaHora());
        cita.setEstado(request.estado());
        cita.setMotivo(request.motivo());
        return toResponse(citaRepository.save(cita));
    }

    @Transactional
    public void eliminar(Long id) {
        Cita cita = citaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada: " + id));
        cita.setActivo(false);
        citaRepository.save(cita);
    }

    private Usuario usuarioActual(String email) {
        return usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + email));
    }

    private Mascota resolverMascota(Long mascotaId) {
        return mascotaRepository.findByIdAndActivoTrue(mascotaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + mascotaId));
    }

    private Usuario resolverVeterinario(Long veterinarioId) {
        Usuario veterinario = usuarioRepository.findById(veterinarioId)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Veterinario no encontrado: " + veterinarioId));
        if (veterinario.getRol() != Rol.ROLE_VETERINARIO) {
            throw new IllegalArgumentException(
                    "El usuario asignado como veterinario debe tener rol ROLE_VETERINARIO: " + veterinarioId);
        }
        return veterinario;
    }

    private boolean tieneAccesoGlobal(Rol rol) {
        return rol == Rol.ROLE_ADMIN || rol == Rol.ROLE_VETERINARIO || rol == Rol.ROLE_AUXILIAR;
    }

    private void verificarAccesoLectura(Usuario usuario, Cita cita) {
        verificarAccesoLecturaMascota(usuario, cita.getMascota());
    }

    /**
     * Extraída de verificarAccesoLectura(Usuario, Cita) para reutilizarla en
     * listarPorMascota(), donde todavía no hay ninguna Cita concreta sobre
     * la que comprobar propiedad (puede haber cero).
     */
    private void verificarAccesoLecturaMascota(Usuario usuario, Mascota mascota) {
        if (!tieneAccesoGlobal(usuario.getRol()) && !mascota.getDuenio().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta cita.");
        }
    }

    private void verificarPermisoEscritura(Usuario usuario, Cita cita) {
        if (usuario.getRol() == Rol.ROLE_VETERINARIO && !cita.getVeterinario().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("Solo puede modificar las citas asignadas a usted.");
        }
    }

    private CitaResponse toResponse(Cita cita) {
        return new CitaResponse(
                cita.getId(),
                cita.getMascota().getId(),
                cita.getMascota().getNombre(),
                cita.getVeterinario().getId(),
                cita.getVeterinario().getNombre(),
                cita.getFechaHora(),
                cita.getEstado(),
                cita.getMotivo(),
                cita.isActivo(),
                cita.getCreadoEn(),
                cita.getActualizadoEn()
        );
    }
}
