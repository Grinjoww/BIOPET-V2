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

import java.time.Instant;

/**
 * CRUD de citas (agendamiento previo de atención veterinaria). No reemplaza ni
 * duplica el futuro módulo de "Consulta" (registro clínico posterior), que
 * pertenece a otro integrante del equipo.
 * <p>
 * Reglas de acceso (aplicadas aquí porque dependen de datos, no solo del rol;
 * el control por rol "puro" ya vive en {@code CitaController} vía @PreAuthorize):
 * <ul>
 *   <li>DUENO: solo lee citas de sus propias mascotas (igual que MascotaService).</li>
 *   <li>VETERINARIO: lee y escribe ÚNICAMENTE las citas donde él es el
 *       veterinario asignado (fase "corrección final demo local" — antes
 *       leía todas, solo la escritura estaba restringida).</li>
 *   <li>ADMIN/AUXILIAR: sin restricciones adicionales de datos.</li>
 * </ul>
 */
@Service
public class CitaService {
    /** Ver AuditoriaEventoRepository/AuditoriaService: Postgres (a diferencia
     *  de H2) falla al inferir el tipo de un parámetro TIMESTAMPTZ NULL
     *  ligado dos veces en la misma consulta -nunca se pasa null a
     *  CitaRepository.buscar, siempre estos límites reales. */
    private static final Instant DESDE_POR_DEFECTO = Instant.EPOCH;
    private static final Instant HASTA_POR_DEFECTO = Instant.parse("9999-12-31T23:59:59Z");

    private final CitaRepository citaRepository;
    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;

    public CitaService(CitaRepository citaRepository, MascotaRepository mascotaRepository, UsuarioRepository usuarioRepository) {
        this.citaRepository = citaRepository;
        this.mascotaRepository = mascotaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * GET /api/citas con filtros server-side (mascota/veterinario/estado/
     * rango de fechas) y alcance por rol. Todos los filtros son opcionales.
     *
     * <p>{@code veterinarioIdFiltro} es lo que el CLIENTE pide filtrar (un
     * ADMIN/AUXILIAR mirando "solo las citas del Dr. X"); el alcance por rol
     * (DUENO/VETERINARIO) se calcula aquí y SIEMPRE gana sobre lo pedido -un
     * ROLE_VETERINARIO nunca puede ver citas de otro veterinario cambiando
     * este parámetro-.
     */
    @Transactional(readOnly = true)
    public Page<CitaResponse> buscar(Pageable pageable, String email, Long mascotaId, Long veterinarioIdFiltro,
                                      EstadoCita estado, Instant desde, Instant hasta) {
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
        return citaRepository.buscar(mascotaId, veterinarioId, duenioId, estado, desdeEfectivo, hastaEfectiva, pageable)
                .map(this::toResponse);
    }

    /**
     * GET /api/citas/mascota/{mascotaId}. Solo lectura, misma regla de
     * alcance que {@link #buscar}: DUENO solo si es su mascota (403 si no),
     * VETERINARIO ve únicamente sus propias citas para esa mascota (no 403:
     * simplemente puede ser una página vacía si nunca atendió a esa mascota).
     */
    @Transactional(readOnly = true)
    public Page<CitaResponse> listarPorMascota(Long mascotaId, Pageable pageable, String email) {
        Usuario usuario = usuarioActual(email);
        Mascota mascota = resolverMascota(mascotaId);
        if (usuario.getRol() == Rol.ROLE_DUENO && !mascota.getDuenio().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta cita.");
        }
        Long veterinarioId = usuario.getRol() == Rol.ROLE_VETERINARIO ? usuario.getId() : null;
        return citaRepository.buscar(mascotaId, veterinarioId, null, null, DESDE_POR_DEFECTO, HASTA_POR_DEFECTO, pageable)
                .map(this::toResponse);
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

    /**
     * GET /api/citas/{id}: DUENO solo si es su mascota, VETERINARIO solo si
     * es su cita asignada, ADMIN/AUXILIAR sin restricción.
     */
    private void verificarAccesoLectura(Usuario usuario, Cita cita) {
        if (usuario.getRol() == Rol.ROLE_DUENO && !cita.getMascota().getDuenio().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta cita.");
        }
        if (usuario.getRol() == Rol.ROLE_VETERINARIO && !cita.getVeterinario().getId().equals(usuario.getId())) {
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
