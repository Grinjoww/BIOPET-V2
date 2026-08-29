package com.biopet.facturacion.service;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.dto.DatosFacturacionRequest;
import com.biopet.facturacion.dto.DatosFacturacionResponse;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.repository.DatosFacturacionRepository;
import com.biopet.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cierra el hueco operativo de la Fase 8A: alta, edicion, listado y manejo del
 * perfil predeterminado de {@code DatosFacturacion} por REST.
 *
 * <h2>Ownership real, no solo {@code @PreAuthorize}</h2>
 *
 * <p>El controlador solo filtra por ROL ({@code @PreAuthorize}); la
 * comprobacion de que un DUENO solo puede tocar SU PROPIO {@code usuarioId}
 * -el del path- vive aqui, en {@link #exigirAcceso}, contra el usuario real
 * autenticado, nunca confiando en el path por si solo. Un DUENO que pida el
 * {@code usuarioId} de otro recibe {@link AccessDeniedException} (403), igual
 * que hace {@code FacturaConsultaService} con las facturas ajenas -nunca 404,
 * para no filtrar si ese id existe-.
 *
 * <p>ADMIN y AUXILIAR operan sobre cualquier {@code usuarioId}: AUXILIAR lo
 * necesita para preparar la facturacion de cualquier cliente. VETERINARIO no
 * llega aqui en absoluto -ver el {@code @PreAuthorize} del controlador-.
 *
 * <h2>Predeterminado: transaccional y protegido por el indice de BD</h2>
 *
 * <p>Ver {@link #aplicarPredeterminado}.
 */
@Service
public class DatosFacturacionService {

    private final DatosFacturacionRepository datosFacturacionRepository;
    private final UsuarioRepository usuarioRepository;

    public DatosFacturacionService(DatosFacturacionRepository datosFacturacionRepository,
                                   UsuarioRepository usuarioRepository) {
        this.datosFacturacionRepository = datosFacturacionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<DatosFacturacionResponse> listar(Long usuarioId, String email) {
        exigirAcceso(usuarioActual(email), usuarioId);
        usuarioObjetivo(usuarioId);
        return datosFacturacionRepository.findAllByUsuario_IdAndActivoTrue(usuarioId).stream()
                .map(DatosFacturacionService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DatosFacturacionResponse buscar(Long usuarioId, Long id, String email) {
        exigirAcceso(usuarioActual(email), usuarioId);
        return toResponse(propios(usuarioId, id));
    }

    @Transactional(readOnly = true)
    public DatosFacturacionResponse obtenerPredeterminado(Long usuarioId, String email) {
        exigirAcceso(usuarioActual(email), usuarioId);
        usuarioObjetivo(usuarioId);
        return toResponse(datosFacturacionRepository
                .findByUsuario_IdAndPredeterminadoTrueAndActivoTrue(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El usuario " + usuarioId + " no tiene datos de facturacion predeterminados.")));
    }

    @Transactional
    public DatosFacturacionResponse crear(Long usuarioId, DatosFacturacionRequest request, String email) {
        exigirAcceso(usuarioActual(email), usuarioId);
        Usuario destino = usuarioObjetivo(usuarioId);

        DatosFacturacion datos = DatosFacturacion.builder()
                .usuario(destino)
                .tipoIdentificacion(request.tipoIdentificacion())
                .identificacion(request.identificacion().trim())
                .razonSocial(request.razonSocial().trim())
                .direccion(blankToNull(request.direccion()))
                .telefono(blankToNull(request.telefono()))
                .emailFacturacion(blankToNull(request.emailFacturacion()))
                .predeterminado(false)
                .activo(true)
                .build();
        DatosFacturacion guardado = datosFacturacionRepository.saveAndFlush(datos);

        // La PRIMERA identidad activa de un usuario se vuelve predeterminada
        // sola: sin esto, un usuario con exactamente un perfil no tendria
        // ninguno predeterminado hasta que alguien llamase al PATCH a mano, y
        // "obtener el perfil predeterminado" (seccion 9.6) fallaria sin motivo
        // para el caso mas comun. No es un campo que el cliente controle -ver
        // el javadoc de DatosFacturacionRequest-, es una consecuencia
        // deterministica de no tener ninguno todavia.
        if (datosFacturacionRepository.findByUsuario_IdAndPredeterminadoTrueAndActivoTrue(usuarioId).isEmpty()) {
            aplicarPredeterminado(usuarioId, guardado.getId());
        }

        return toResponse(datosFacturacionRepository.findById(guardado.getId()).orElseThrow());
    }

    @Transactional
    public DatosFacturacionResponse actualizar(Long usuarioId, Long id, DatosFacturacionRequest request,
                                               String email) {
        exigirAcceso(usuarioActual(email), usuarioId);
        DatosFacturacion datos = propios(usuarioId, id);

        datos.setTipoIdentificacion(request.tipoIdentificacion());
        datos.setIdentificacion(request.identificacion().trim());
        datos.setRazonSocial(request.razonSocial().trim());
        datos.setDireccion(blankToNull(request.direccion()));
        datos.setTelefono(blankToNull(request.telefono()));
        datos.setEmailFacturacion(blankToNull(request.emailFacturacion()));
        return toResponse(datosFacturacionRepository.save(datos));
    }

    @Transactional
    public DatosFacturacionResponse marcarPredeterminado(Long usuarioId, Long id, String email) {
        exigirAcceso(usuarioActual(email), usuarioId);
        propios(usuarioId, id); // 403/404 ANTES de tocar nada
        aplicarPredeterminado(usuarioId, id);
        return toResponse(datosFacturacionRepository.findById(id).orElseThrow());
    }

    /** Baja logica: nunca DELETE fisico (puede ser el snapshot de facturas ya emitidas). */
    @Transactional
    public void desactivar(Long usuarioId, Long id, String email) {
        exigirAcceso(usuarioActual(email), usuarioId);
        DatosFacturacion datos = propios(usuarioId, id);
        datos.setActivo(false);
        datos.setPredeterminado(false);
        datosFacturacionRepository.save(datos);
    }

    // ==================================================================
    // Predeterminado: unico por usuario, transaccional
    // ==================================================================

    /**
     * Dos pasos, en la MISMA transaccion: (1) apaga cualquier predeterminado
     * previo del usuario, (2) enciende el nuevo. Ninguno de los dos pasos por
     * si solo evita que dos peticiones concurrentes intenten fijar DOS
     * predeterminados DISTINTOS del mismo usuario a la vez -ver el escenario
     * completo en {@code DatosFacturacionConcurrenciaIntegrationTest}-: la
     * garantia final de que nunca queden dos no la da este metodo, la da
     * {@code idx_datos_facturacion_predeterminado_unico} (indice unico parcial
     * de V7). Si dos transacciones chocan, PostgreSQL deja pasar a una y
     * rechaza la otra con una violacion de restriccion, que
     * {@code GlobalExceptionHandler} traduce a 409. Es exactamente el mismo
     * principio que {@code SecuencialService}: la exclusion mutua la garantiza
     * la base de datos, no un candado en memoria de esta JVM.
     */
    private void aplicarPredeterminado(Long usuarioId, Long id) {
        datosFacturacionRepository.limpiarPredeterminado(usuarioId);
        int actualizados = datosFacturacionRepository.marcarPredeterminado(id, usuarioId);
        if (actualizados == 0) {
            throw new RecursoNoEncontradoException(
                    "No se encontraron datos de facturacion activos con id " + id
                            + " para el usuario " + usuarioId + ".");
        }
    }

    // ==================================================================
    // Apoyo
    // ==================================================================

    private Usuario usuarioActual(String email) {
        return usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + email));
    }

    private Usuario usuarioObjetivo(Long usuarioId) {
        return usuarioRepository.findByIdAndActivoTrue(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + usuarioId));
    }

    /**
     * Un DUENO solo puede operar sobre SU PROPIO {@code usuarioId}; ADMIN y
     * AUXILIAR sobre cualquiera. Se comprueba contra el usuario AUTENTICADO
     * real, nunca contra lo que afirme el path.
     */
    private void exigirAcceso(Usuario autenticado, Long usuarioId) {
        boolean accesoGlobal = autenticado.getRol() == Rol.ROLE_ADMIN || autenticado.getRol() == Rol.ROLE_AUXILIAR;
        if (!accesoGlobal && !autenticado.getId().equals(usuarioId)) {
            throw new AccessDeniedException(
                    "No tiene permisos para acceder a los datos de facturacion de otro usuario.");
        }
    }

    private DatosFacturacion propios(Long usuarioId, Long id) {
        return datosFacturacionRepository.findByIdAndUsuario_IdAndActivoTrue(id, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontraron datos de facturacion activos con id " + id
                                + " para el usuario " + usuarioId + "."));
    }

    private static String blankToNull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private static DatosFacturacionResponse toResponse(DatosFacturacion d) {
        return new DatosFacturacionResponse(
                d.getId(), d.getTipoIdentificacion(), d.getIdentificacion(), d.getRazonSocial(),
                d.getDireccion(), d.getTelefono(), d.getEmailFacturacion(), d.isPredeterminado(), d.isActivo());
    }
}
