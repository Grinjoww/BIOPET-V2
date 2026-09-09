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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * Selector BUSCABLE de "mascota" al crear/editar una cita, consulta,
     * vacuna o factura (mascotaId) -auditoría de usabilidad con datos
     * masivos: con 10.000 mascotas, esos formularios cargaban solo las
     * primeras 200 (alfabéticas) y el resto quedaba, en la práctica,
     * imposible de elegir. Método SEPARADO de {@link #listar} -y sin
     * {@code @Cacheable}- a propósito: es texto libre de alta variabilidad,
     * cachearlo no aportaría hit-rate real y arriesgaría devolver resultados
     * de una búsqueda anterior para otra.
     *
     * <p>{@code duenioIdFiltro} es opcional y solo lo usa ADMIN/VETERINARIO/
     * AUXILIAR (ver factura-nueva: tras elegir dueño, la mascota se busca YA
     * acotada a ese dueño -antes se traían 200 mascotas de golpe y se
     * filtraban en el cliente-). Para ROLE_DUENO no cambia nada: sigue
     * viendo solo lo suyo sin importar qué llegue en ese parámetro.
     */
    @Transactional(readOnly = true)
    public Page<MascotaResponse> buscarSeleccionables(Pageable pageable, String email, String q, Long duenioIdFiltro) {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        String texto = (q == null || q.isBlank()) ? null : q.trim();
        Long idExacto = idSiEsNumerico(texto);

        if (usuario.getRol() == Rol.ROLE_DUENO) {
            return mascotaRepository.buscarActivasPorDuenio(usuario.getId(), texto, idExacto, pageable)
                    .map(this::toResponse);
        }
        if (duenioIdFiltro != null) {
            return mascotaRepository.buscarActivasPorDuenio(duenioIdFiltro, texto, idExacto, pageable)
                    .map(this::toResponse);
        }
        return mascotaRepository.buscarActivas(texto, idExacto, pageable).map(this::toResponse);
    }

    /** Permite buscar mascotas por id exacto cuando el texto escrito es puramente numérico (p.ej. "5" o el id del expediente). */
    /**
     * Reconoce tanto un id "pelado" (p.ej. "7") como el expediente visual
     * que ve el usuario en pantalla (formatearExpediente en el frontend:
     * "EXP-000007") -listado de mascotas, fase "demo local": buscar por
     * expediente debe funcionar tal cual se muestra, no solo con el id crudo-.
     */
    private static final Pattern PATRON_ID_O_EXPEDIENTE = Pattern.compile("(?i)^(?:exp-?)?0*(\\d+)$");

    private Long idSiEsNumerico(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher m = PATRON_ID_O_EXPEDIENTE.matcher(texto.trim());
        if (!m.matches()) {
            return null;
        }
        try {
            return Long.valueOf(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
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

        // ADMIN: respeta el duenioId solicitado (o global si no se envía).
        // VETERINARIO/AUXILIAR: acceso clínico global, un duenioId de cliente se ignora.
        // DUENO: siempre su propio id, nunca el de un duenioId ajeno enviado por el cliente.
        Long duenioIdEfectivo = switch (usuarioAutenticado.getRol()) {
            case ROLE_ADMIN -> duenioIdSolicitado;
            case ROLE_VETERINARIO, ROLE_AUXILIAR -> null;
            case ROLE_DUENO -> usuarioAutenticado.getId();
        };

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