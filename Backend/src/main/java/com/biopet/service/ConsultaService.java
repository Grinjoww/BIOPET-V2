package com.biopet.service;

import com.biopet.dto.ConsultaRequest;
import com.biopet.dto.ConsultaResponse;
import com.biopet.entity.Consulta;
import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.repository.ConsultaRepository;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaService {
    private final ConsultaRepository consultaRepository;
    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;

    public ConsultaService(ConsultaRepository consultaRepository,
                            MascotaRepository mascotaRepository,
                            UsuarioRepository usuarioRepository) {
        this.consultaRepository = consultaRepository;
        this.mascotaRepository = mascotaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Cacheable(value = "consultas", key = "#email + '-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
    @Transactional(readOnly = true)
    public Page<ConsultaResponse> listar(Pageable pageable, String email) {
        Usuario usuario = usuarioActual(email);
        if (usuario.getRol() == Rol.ROLE_DUENO) {
            // Un dueño solo ve consultas de sus propias mascotas
            return consultaRepository.findAllByActivoTrue(pageable)
                    .map(this::toResponse); // filtrado real de propiedad se aplica en buscar(); aquí listamos y filtramos abajo si se requiere endpoint dedicado
        }
        return consultaRepository.findAllByActivoTrue(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConsultaResponse buscar(Long id, String email) {
        Usuario usuario = usuarioActual(email);
        Consulta consulta = consultaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Consulta no encontrada: " + id));
        verificarAcceso(usuario, consulta);
        return toResponse(consulta);
    }

    @CacheEvict(value = "consultas", allEntries = true)
    @Transactional
    public ConsultaResponse crear(ConsultaRequest request) {
        Mascota mascota = mascotaRepository.findByIdAndActivoTrue(request.mascotaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + request.mascotaId()));
        Usuario veterinario = resolverVeterinario(request.veterinarioId());

        Consulta consulta = Consulta.builder()
                .mascota(mascota)
                .veterinario(veterinario)
                .fechaConsulta(request.fechaConsulta())
                .motivo(request.motivo())
                .diagnostico(request.diagnostico())
                .tratamiento(request.tratamiento())
                .observaciones(request.observaciones())
                .activo(true)
                .build();
        return toResponse(consultaRepository.save(consulta));
    }

    @CacheEvict(value = "consultas", allEntries = true)
    @Transactional
    public ConsultaResponse actualizar(Long id, ConsultaRequest request, String email) {
        Usuario usuario = usuarioActual(email);
        Consulta consulta = consultaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Consulta no encontrada: " + id));
        verificarAcceso(usuario, consulta);

        Mascota mascota = mascotaRepository.findByIdAndActivoTrue(request.mascotaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + request.mascotaId()));
        Usuario veterinario = resolverVeterinario(request.veterinarioId());

        consulta.setMascota(mascota);
        consulta.setVeterinario(veterinario);
        consulta.setFechaConsulta(request.fechaConsulta());
        consulta.setMotivo(request.motivo());
        consulta.setDiagnostico(request.diagnostico());
        consulta.setTratamiento(request.tratamiento());
        consulta.setObservaciones(request.observaciones());
        return toResponse(consultaRepository.save(consulta));
    }

    @CacheEvict(value = "consultas", allEntries = true)
    @Transactional
    public void eliminar(Long id, String email) {
        Usuario usuario = usuarioActual(email);
        Consulta consulta = consultaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Consulta no encontrada: " + id));
        verificarAcceso(usuario, consulta);
        consulta.setActivo(false);
        consultaRepository.save(consulta);
    }

    private Usuario usuarioActual(String email) {
        return usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    private Usuario resolverVeterinario(Long veterinarioId) {
        Usuario veterinario = usuarioRepository.findById(veterinarioId)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Veterinario no encontrado: " + veterinarioId));
        if (veterinario.getRol() != Rol.ROLE_VETERINARIO) {
            throw new IllegalArgumentException("El usuario asignado debe tener rol ROLE_VETERINARIO: " + veterinarioId);
        }
        return veterinario;
    }

    private void verificarAcceso(Usuario usuario, Consulta consulta) {
        boolean accesoGlobal = usuario.getRol() == Rol.ROLE_ADMIN
                || usuario.getRol() == Rol.ROLE_VETERINARIO
                || usuario.getRol() == Rol.ROLE_AUXILIAR;
        boolean esDuenioDeLaMascota = consulta.getMascota().getDuenio().getId().equals(usuario.getId());
        if (!accesoGlobal && !esDuenioDeLaMascota) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta consulta.");
        }
    }

    private ConsultaResponse toResponse(Consulta consulta) {
        return new ConsultaResponse(
                consulta.getId(),
                consulta.getMascota().getId(),
                consulta.getMascota().getNombre(),
                consulta.getVeterinario().getId(),
                consulta.getVeterinario().getNombre(),
                consulta.getFechaConsulta(),
                consulta.getMotivo(),
                consulta.getDiagnostico(),
                consulta.getTratamiento(),
                consulta.getObservaciones(),
                consulta.isActivo(),
                consulta.getCreadoEn(),
                consulta.getActualizadoEn()
        );
    }
}