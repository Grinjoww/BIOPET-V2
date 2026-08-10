package com.biopet.service;

import com.biopet.dto.VacunaRequest;
import com.biopet.dto.VacunaResponse;
import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.entity.Vacuna;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import com.biopet.repository.VacunaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VacunaService {
    private final VacunaRepository vacunaRepository;
    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;

    public VacunaService(VacunaRepository vacunaRepository,
                          MascotaRepository mascotaRepository,
                          UsuarioRepository usuarioRepository) {
        this.vacunaRepository = vacunaRepository;
        this.mascotaRepository = mascotaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public Page<VacunaResponse> listar(Pageable pageable, String email) {
        Usuario usuario = usuarioActivo(email);
        if (usuario.getRol() == Rol.ROLE_DUENO) {
            return vacunaRepository.findAllByMascota_Duenio_IdAndActivoTrue(usuario.getId(), pageable)
                    .map(this::toResponse);
        }
        return vacunaRepository.findAllByActivoTrue(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<VacunaResponse> listarPorMascota(Long mascotaId, Pageable pageable, String email) {
        Usuario usuario = usuarioActivo(email);
        Mascota mascota = mascotaActiva(mascotaId);
        verificarAcceso(usuario, mascota);
        return vacunaRepository.findAllByMascotaIdAndActivoTrue(mascotaId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VacunaResponse buscar(Long id, String email) {
        Usuario usuario = usuarioActivo(email);
        Vacuna vacuna = vacunaActiva(id);
        verificarAcceso(usuario, vacuna.getMascota());
        return toResponse(vacuna);
    }

    @Transactional
    public VacunaResponse crear(VacunaRequest request) {
        Mascota mascota = mascotaActiva(request.mascotaId());
        Usuario veterinario = resolverVeterinario(request.veterinarioId());
        Vacuna vacuna = Vacuna.builder()
                .mascota(mascota)
                .veterinario(veterinario)
                .tipo(request.tipo())
                .fechaAplicacion(request.fechaAplicacion())
                .proximaFecha(request.proximaFecha())
                .observaciones(request.observaciones())
                .activo(true)
                .build();
        return toResponse(vacunaRepository.save(vacuna));
    }

    @Transactional
    public VacunaResponse actualizar(Long id, VacunaRequest request, String email) {
        Usuario usuario = usuarioActivo(email);
        Vacuna vacuna = vacunaActiva(id);
        verificarAcceso(usuario, vacuna.getMascota());

        Mascota mascota = mascotaActiva(request.mascotaId());
        Usuario veterinario = resolverVeterinario(request.veterinarioId());

        vacuna.setMascota(mascota);
        vacuna.setVeterinario(veterinario);
        vacuna.setTipo(request.tipo());
        vacuna.setFechaAplicacion(request.fechaAplicacion());
        vacuna.setProximaFecha(request.proximaFecha());
        vacuna.setObservaciones(request.observaciones());
        return toResponse(vacunaRepository.save(vacuna));
    }

    @Transactional
    public void eliminar(Long id, String email) {
        Usuario usuario = usuarioActivo(email);
        Vacuna vacuna = vacunaActiva(id);
        verificarAcceso(usuario, vacuna.getMascota());
        vacuna.setActivo(false);
        vacunaRepository.save(vacuna);
    }

    // ---------- Helpers ----------

    private Usuario usuarioActivo(String email) {
        return usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    private Mascota mascotaActiva(Long mascotaId) {
        return mascotaRepository.findByIdAndActivoTrue(mascotaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + mascotaId));
    }

    private Vacuna vacunaActiva(Long id) {
        return vacunaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vacuna no encontrada: " + id));
    }

    private Usuario resolverVeterinario(Long veterinarioId) {
        if (veterinarioId == null) return null;
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

    private void verificarAcceso(Usuario usuario, Mascota mascota) {
        if (!tieneAccesoGlobal(usuario.getRol()) && !mascota.getDuenio().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta vacuna.");
        }
    }

    private VacunaResponse toResponse(Vacuna vacuna) {
        Usuario veterinario = vacuna.getVeterinario();
        return new VacunaResponse(
                vacuna.getId(),
                vacuna.getMascota().getId(),
                vacuna.getMascota().getNombre(),
                veterinario != null ? veterinario.getId() : null,
                veterinario != null ? veterinario.getNombre() : null,
                vacuna.getTipo(),
                vacuna.getFechaAplicacion(),
                vacuna.getProximaFecha(),
                vacuna.getObservaciones(),
                vacuna.isActivo(),
                vacuna.getCreadoEn(),
                vacuna.getActualizadoEn()
        );
    }
}
