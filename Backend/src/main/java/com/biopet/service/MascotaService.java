package com.biopet.service;

import com.biopet.dto.MascotaRequest;
import com.biopet.dto.MascotaResponse;
import com.biopet.dto.ResumenEspecieResponse;
import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.ProcedimientoBiopetRepository;
import com.biopet.repository.UsuarioRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MascotaService {
    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProcedimientoBiopetRepository procedimientoBiopetRepository;

    public MascotaService(MascotaRepository mascotaRepository,
                          UsuarioRepository usuarioRepository,
                          ProcedimientoBiopetRepository procedimientoBiopetRepository) {
        this.mascotaRepository = mascotaRepository;
        this.usuarioRepository = usuarioRepository;
        this.procedimientoBiopetRepository = procedimientoBiopetRepository;
    }

    @Cacheable(value = "mascotas", key = "#email + '-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
    @Transactional(readOnly = true)
    public Page<MascotaResponse> listar(Pageable pageable, String email) {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        if (usuario.getRol() == Rol.ROLE_DUENO) {
            return mascotaRepository.findAllByDuenioIdAndActivoTrue(usuario.getId(), pageable).map(this::toResponse);
        }
        return mascotaRepository.findAllByActivoTrue(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MascotaResponse buscar(Long id, String email) {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        Mascota mascota = mascotaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + id));
        verificarPropiedad(usuario, mascota);
        return toResponse(mascota);
    }

    @CacheEvict(value = "mascotas", allEntries = true)
    @Transactional
    public MascotaResponse crear(MascotaRequest request) {
        Usuario duenio = resolverDuenio(request.duenioId());
        Mascota mascota = Mascota.builder()
                .duenio(duenio)
                .nombre(request.nombre())
                .especie(request.especie())
                .raza(request.raza())
                .fechaNacimiento(request.fechaNacimiento())
                .activo(true)
                .build();
        return toResponse(mascotaRepository.save(mascota));
    }

    @CacheEvict(value = "mascotas", allEntries = true)
    @Transactional
    public MascotaResponse actualizar(Long id, MascotaRequest request, String email) {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        Mascota mascota = mascotaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + id));
        verificarPropiedad(usuario, mascota);
        Usuario duenio = resolverDuenio(request.duenioId());
        mascota.setDuenio(duenio);
        mascota.setNombre(request.nombre());
        mascota.setEspecie(request.especie());
        mascota.setRaza(request.raza());
        mascota.setFechaNacimiento(request.fechaNacimiento());
        return toResponse(mascotaRepository.save(mascota));
    }

    @CacheEvict(value = "mascotas", allEntries = true)
    @Transactional
    public void eliminar(Long id, String email) {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        Mascota mascota = mascotaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + id));
        verificarPropiedad(usuario, mascota);
        mascota.setActivo(false);
        mascotaRepository.save(mascota);
    }

    @Transactional(readOnly = true)
    public List<ResumenEspecieResponse> resumenPorEspecie(Long duenioIdSolicitado, String emailAutenticado) {
        Usuario usuarioAutenticado = usuarioRepository.findByEmailAndActivoTrue(emailAutenticado)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + emailAutenticado));

        Long duenioIdEfectivo = (usuarioAutenticado.getRol() == Rol.ROLE_ADMIN)
                ? duenioIdSolicitado
                : usuarioAutenticado.getId();

        return procedimientoBiopetRepository.resumenPorEspecie(duenioIdEfectivo).stream()
                .map(r -> new ResumenEspecieResponse(r.getEspecie(), r.getTotal()))
                .toList();
    }

    private boolean tieneAccesoGlobal(Rol rol) {
        return rol == Rol.ROLE_ADMIN || rol == Rol.ROLE_VETERINARIO || rol == Rol.ROLE_AUXILIAR;
    }

    private void verificarPropiedad(Usuario usuario, Mascota mascota) {
        if (!tieneAccesoGlobal(usuario.getRol()) && !mascota.getDuenio().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta mascota.");
        }
    }

    private Usuario resolverDuenio(Long duenioId) {
        Usuario duenio = usuarioRepository.findById(duenioId)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Dueño no encontrado: " + duenioId));
        if (duenio.getRol() != Rol.ROLE_DUENO) {
            throw new IllegalArgumentException("El usuario asignado como dueño debe tener rol ROLE_DUENO: " + duenioId);
        }
        return duenio;
    }

    private MascotaResponse toResponse(Mascota mascota) {
        return new MascotaResponse(
                mascota.getId(),
                mascota.getDuenio().getId(),
                mascota.getDuenio().getNombre(),
                mascota.getNombre(),
                mascota.getEspecie(),
                mascota.getRaza(),
                mascota.getFechaNacimiento(),
                mascota.isActivo(),
                mascota.getCreadoEn(),
                mascota.getActualizadoEn()
        );
    }
}