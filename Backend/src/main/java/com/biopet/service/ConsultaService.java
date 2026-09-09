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

import java.time.Instant;

/**
 * Reglas de acceso a datos (fase "corrección final demo local"):
 * <ul>
 *   <li>DUENO: solo lee consultas de sus propias mascotas.</li>
 *   <li>VETERINARIO: lee y escribe ÚNICAMENTE las consultas donde él es el
 *       veterinario asignado -antes leía y podía modificar/eliminar
 *       CUALQUIER consulta, de cualquier veterinario-.</li>
 *   <li>ADMIN/AUXILIAR: sin restricciones adicionales de datos.</li>
 * </ul>
 */
@Service
public class ConsultaService {
    /** Ver CitaService: nunca se pasa null a ConsultaRepository.buscar para
     *  desde/hasta -Postgres falla al inferir el tipo de un TIMESTAMPTZ NULL
     *  ligado dos veces en la misma consulta-. */
    private static final Instant DESDE_POR_DEFECTO = Instant.EPOCH;
    private static final Instant HASTA_POR_DEFECTO = Instant.parse("9999-12-31T23:59:59Z");

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
        return buscarInterno(pageable, email, null, null, null, null, null);
    }

    /**
     * GET /api/consultas con filtros server-side (mascota/veterinario/texto/
     * rango de fechas). Sin cache a propósito -texto libre de alta
     * variabilidad, ver MascotaService.buscarSeleccionables-.
     */
    @Transactional(readOnly = true)
    public Page<ConsultaResponse> buscar(Pageable pageable, String email, Long mascotaId, Long veterinarioIdFiltro,
                                          String q, Instant desde, Instant hasta) {
        return buscarInterno(pageable, email, mascotaId, veterinarioIdFiltro, q, desde, hasta);
    }

    private Page<ConsultaResponse> buscarInterno(Pageable pageable, String email, Long mascotaId,
                                                  Long veterinarioIdFiltro, String q, Instant desde, Instant hasta) {
        Usuario usuario = usuarioActual(email);
        Long duenioId = null;
        Long veterinarioId = veterinarioIdFiltro;
        if (usuario.getRol() == Rol.ROLE_DUENO) {
            duenioId = usuario.getId();
        } else if (usuario.getRol() == Rol.ROLE_VETERINARIO) {
            veterinarioId = usuario.getId();
        }
        Instant desdeEfectivo = desde != null ? desde : DESDE_POR_DEFECTO;
        Instant hastaEfectiva = hasta != null ? hasta : HASTA_POR_DEFECTO;
        // Nunca se pasa null a ConsultaRepository.buscar: "%" casa con todo
        // cuando no hay texto que filtrar (ver el javadoc del repositorio).
        String patronTexto = (q == null || q.isBlank()) ? "%" : "%" + q.trim() + "%";
        return consultaRepository.buscar(mascotaId, veterinarioId, duenioId, patronTexto, desdeEfectivo, hastaEfectiva, pageable)
                .map(this::toResponse);
    }

    /**
     * GET /api/consultas/mascota/{mascotaId}. DUENO solo si es su mascota
     * (403 si no); VETERINARIO ve únicamente sus propias consultas para esa
     * mascota (página vacía si nunca la atendió, no 403: la mascota en sí
     * puede seguir siendo visible para él).
     */
    @Transactional(readOnly = true)
    public Page<ConsultaResponse> listarPorMascota(Long mascotaId, Pageable pageable, String email) {
        Usuario usuario = usuarioActual(email);
        Mascota mascota = mascotaActiva(mascotaId);
        if (usuario.getRol() == Rol.ROLE_DUENO && !mascota.getDuenio().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta consulta.");
        }
        Long veterinarioId = usuario.getRol() == Rol.ROLE_VETERINARIO ? usuario.getId() : null;
        return consultaRepository.buscar(mascotaId, veterinarioId, null, "%", DESDE_POR_DEFECTO, HASTA_POR_DEFECTO, pageable)
                .map(this::toResponse);
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

    private Mascota mascotaActiva(Long mascotaId) {
        return mascotaRepository.findByIdAndActivoTrue(mascotaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + mascotaId));
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

    /**
     * GET /api/consultas/{id} y también gate de escritura (actualizar/
     * eliminar): DUENO solo si es su mascota, VETERINARIO solo si es SU
     * consulta asignada, ADMIN/AUXILIAR sin restricción. Antes un
     * VETERINARIO podía leer/modificar/eliminar la consulta de CUALQUIER
     * otro veterinario -aquí es donde se corrige-.
     */
    private void verificarAcceso(Usuario usuario, Consulta consulta) {
        if (usuario.getRol() == Rol.ROLE_DUENO && !consulta.getMascota().getDuenio().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta consulta.");
        }
        if (usuario.getRol() == Rol.ROLE_VETERINARIO && !consulta.getVeterinario().getId().equals(usuario.getId())) {
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