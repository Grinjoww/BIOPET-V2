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

import java.time.LocalDate;

/**
 * Reglas de acceso a datos (fase "corrección final demo local"):
 * <ul>
 *   <li>DUENO: solo lee vacunas de sus propias mascotas.</li>
 *   <li>VETERINARIO: lee y escribe ÚNICAMENTE las vacunas donde él es el
 *       veterinario asignado -antes leía y podía modificar/eliminar
 *       CUALQUIER vacuna, de cualquier veterinario, o sin veterinario-.
 *       Una vacuna sin veterinario asignado no es "de" ningún veterinario:
 *       queda fuera del alcance de todos ellos hasta que alguien la asigne.</li>
 *   <li>ADMIN/AUXILIAR: sin restricciones adicionales de datos.</li>
 * </ul>
 */
@Service
public class VacunaService {
    /** Ver CitaService: mismo criterio "nunca null" por consistencia, aunque
     *  fechaAplicacion es DATE (no TIMESTAMPTZ). */
    private static final LocalDate DESDE_POR_DEFECTO = LocalDate.of(1900, 1, 1);
    private static final LocalDate HASTA_POR_DEFECTO = LocalDate.of(9999, 12, 31);

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
        return buscarInterno(pageable, email, null, null, null, null, null);
    }

    /**
     * GET /api/vacunas con filtros server-side (mascota/veterinario/tipo/
     * rango de fechas).
     */
    @Transactional(readOnly = true)
    public Page<VacunaResponse> buscar(Pageable pageable, String email, Long mascotaId, Long veterinarioIdFiltro,
                                        String tipo, LocalDate desde, LocalDate hasta) {
        return buscarInterno(pageable, email, mascotaId, veterinarioIdFiltro, tipo, desde, hasta);
    }

    private Page<VacunaResponse> buscarInterno(Pageable pageable, String email, Long mascotaId,
                                                Long veterinarioIdFiltro, String tipo, LocalDate desde, LocalDate hasta) {
        Usuario usuario = usuarioActivo(email);
        Long duenioId = null;
        Long veterinarioId = veterinarioIdFiltro;
        if (usuario.getRol() == Rol.ROLE_DUENO) {
            duenioId = usuario.getId();
        } else if (usuario.getRol() == Rol.ROLE_VETERINARIO) {
            veterinarioId = usuario.getId();
        }
        LocalDate desdeEfectivo = desde != null ? desde : DESDE_POR_DEFECTO;
        LocalDate hastaEfectiva = hasta != null ? hasta : HASTA_POR_DEFECTO;
        // Nunca se pasa null a VacunaRepository.buscar: "%" casa con todo
        // cuando no hay tipo que filtrar (ver el javadoc del repositorio).
        String patronTipo = (tipo == null || tipo.isBlank()) ? "%" : "%" + tipo.trim() + "%";
        return vacunaRepository.buscar(mascotaId, veterinarioId, duenioId, patronTipo, desdeEfectivo, hastaEfectiva, pageable)
                .map(this::toResponse);
    }

    /**
     * GET /api/vacunas/mascota/{mascotaId}. DUENO solo si es su mascota
     * (403 si no); VETERINARIO ve únicamente sus propias vacunas para esa
     * mascota.
     */
    @Transactional(readOnly = true)
    public Page<VacunaResponse> listarPorMascota(Long mascotaId, Pageable pageable, String email) {
        Usuario usuario = usuarioActivo(email);
        Mascota mascota = mascotaActiva(mascotaId);
        if (usuario.getRol() == Rol.ROLE_DUENO && !mascota.getDuenio().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta vacuna.");
        }
        Long veterinarioId = usuario.getRol() == Rol.ROLE_VETERINARIO ? usuario.getId() : null;
        return vacunaRepository.buscar(mascotaId, veterinarioId, null, "%", DESDE_POR_DEFECTO, HASTA_POR_DEFECTO, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VacunaResponse buscar(Long id, String email) {
        Usuario usuario = usuarioActivo(email);
        Vacuna vacuna = vacunaActiva(id);
        verificarAcceso(usuario, vacuna);
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
        verificarAcceso(usuario, vacuna);

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
        verificarAcceso(usuario, vacuna);
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

    /**
     * GET /api/vacunas/{id} y también gate de escritura (actualizar/
     * eliminar): DUENO solo si es su mascota, VETERINARIO solo si es SU
     * vacuna asignada (una sin veterinario asignado no es de nadie),
     * ADMIN/AUXILIAR sin restricción.
     */
    private void verificarAcceso(Usuario usuario, Vacuna vacuna) {
        if (usuario.getRol() == Rol.ROLE_DUENO && !vacuna.getMascota().getDuenio().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta vacuna.");
        }
        if (usuario.getRol() == Rol.ROLE_VETERINARIO) {
            Usuario veterinarioAsignado = vacuna.getVeterinario();
            if (veterinarioAsignado == null || !veterinarioAsignado.getId().equals(usuario.getId())) {
                throw new AccessDeniedException("No tiene permisos para acceder a esta vacuna.");
            }
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
