package com.biopet.facturacion.service;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.dto.ActualizarPuntoEmisionRequest;
import com.biopet.facturacion.dto.PuntoEmisionRequest;
import com.biopet.facturacion.dto.PuntoEmisionResponse;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.SecuencialEmision;
import com.biopet.facturacion.exception.ConfiguracionFiscalInvalidaException;
import com.biopet.facturacion.repository.EmisorFiscalRepository;
import com.biopet.facturacion.repository.PuntoEmisionRepository;
import com.biopet.facturacion.repository.SecuencialEmisionRepository;
import com.biopet.facturacion.sri.SriAmbienteProperties;
import com.biopet.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catalogo de puntos de emision para la Fase 8B.
 *
 * <p>ADMIN ve y administra cualquier punto (activo o no); AUXILIAR solo lee
 * los ACTIVOS -es quien elige {@code puntoEmisionId} al preparar una emision,
 * y un punto inactivo no es una opcion valida-. La distincion se resuelve aqui
 * mirando el rol de quien pregunta, no con un parametro de la URL que el
 * cliente pudiese manipular.
 *
 * <p>{@code establecimiento}/{@code puntoEmision} (la serie fiscal) son
 * inmutables una vez creados: ver el javadoc de {@link PuntoEmisionRequest}.
 *
 * <h2>Correccion post-8B: provisionar el {@code SecuencialEmision}</h2>
 *
 * <p>{@link #crear} ya NO deja el punto sin contador: en la MISMA transaccion
 * crea el {@link SecuencialEmision} que le falta para el ambiente EFECTIVO del
 * servidor ({@link SriAmbienteProperties#getAmbiente()}, nunca uno que el
 * cliente pudiese sugerir) con {@code ultimoSecuencial = 0}. Sin esto, un punto
 * creado por REST quedaba inservible para {@code POST /facturas/{id}/emitir}
 * hasta una intervencion manual fuera de la API -exactamente el hueco que esta
 * correccion cierra-.
 *
 * <p>{@link #asegurarSecuencialInicial} es idempotente a proposito: si la fila
 * ya existiera para ese (punto, ambiente) -algo que hoy no puede pasar desde
 * {@link #crear} porque el punto es nuevo, pero que la proteje de todos modos
 * ante cualquier reuso futuro del metodo- NO la toca, NO la resetea. Nunca crea
 * una fila para un ambiente distinto del configurado en el servidor, y ni
 * {@link #actualizar} ni {@link #cambiarEstado} llaman a este metodo: editar la
 * direccion o activar/desactivar un punto nunca debe rozar su secuencial.
 */
@Service
public class PuntoEmisionService {

    private final PuntoEmisionRepository puntoEmisionRepository;
    private final EmisorFiscalRepository emisorFiscalRepository;
    private final SecuencialEmisionRepository secuencialEmisionRepository;
    private final SriAmbienteProperties ambienteProperties;
    private final UsuarioRepository usuarioRepository;

    public PuntoEmisionService(PuntoEmisionRepository puntoEmisionRepository,
                               EmisorFiscalRepository emisorFiscalRepository,
                               SecuencialEmisionRepository secuencialEmisionRepository,
                               SriAmbienteProperties ambienteProperties,
                               UsuarioRepository usuarioRepository) {
        this.puntoEmisionRepository = puntoEmisionRepository;
        this.emisorFiscalRepository = emisorFiscalRepository;
        this.secuencialEmisionRepository = secuencialEmisionRepository;
        this.ambienteProperties = ambienteProperties;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<PuntoEmisionResponse> listar(String email) {
        Usuario usuario = usuarioActual(email);
        List<PuntoEmision> puntos = usuario.getRol() == Rol.ROLE_ADMIN
                ? puntoEmisionRepository.findAll()
                : puntoEmisionRepository.findAllByActivoTrue();
        return puntos.stream().map(PuntoEmisionService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PuntoEmisionResponse buscar(Long id) {
        return toResponse(puntoPorId(id));
    }

    @Transactional
    public PuntoEmisionResponse crear(PuntoEmisionRequest request) {
        EmisorFiscal emisor = emisorFiscalRepository.findById(request.emisorFiscalId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el emisor fiscal con id " + request.emisorFiscalId() + "."));

        // Comprobacion proactiva ademas del indice unico de BD (V7): un
        // mensaje de negocio claro en vez de esperar al DataIntegrityViolationException
        // generico del manejador global.
        puntoEmisionRepository
                .findByEmisorFiscal_IdAndEstablecimientoAndPuntoEmision(
                        emisor.getId(), request.establecimiento(), request.puntoEmision())
                .ifPresent(existente -> {
                    throw new ConfiguracionFiscalInvalidaException(
                            "El emisor " + emisor.getId() + " ya tiene un punto de emision "
                                    + request.establecimiento() + "-" + request.puntoEmision() + ".");
                });

        PuntoEmision punto = PuntoEmision.builder()
                .emisorFiscal(emisor)
                .establecimiento(request.establecimiento())
                .puntoEmision(request.puntoEmision())
                .direccionEstablecimiento(blankToNull(request.direccionEstablecimiento()))
                .activo(true)
                .build();
        PuntoEmision guardado = puntoEmisionRepository.save(punto);

        // Sin esto el punto queda creado pero inservible para emitir: ver el
        // javadoc de la clase. El ambiente SIEMPRE es el del servidor, jamas
        // uno que el request pudiera incluir (PuntoEmisionRequest ni siquiera
        // tiene ese campo).
        asegurarSecuencialInicial(guardado, ambienteProperties.getAmbiente());

        return toResponse(guardado);
    }

    /** Solo la direccion es editable; la serie fiscal es inmutable (ver el DTO). */
    @Transactional
    public PuntoEmisionResponse actualizar(Long id, ActualizarPuntoEmisionRequest request) {
        PuntoEmision punto = puntoPorId(id);
        punto.setDireccionEstablecimiento(blankToNull(request.direccionEstablecimiento()));
        return toResponse(puntoEmisionRepository.save(punto));
    }

    @Transactional
    public PuntoEmisionResponse cambiarEstado(Long id, boolean activo) {
        PuntoEmision punto = puntoPorId(id);
        punto.setActivo(activo);
        return toResponse(puntoEmisionRepository.save(punto));
    }

    /**
     * Crea el {@code SecuencialEmision} de {@code (punto, ambiente)} con
     * {@code ultimoSecuencial = 0} SOLO si todavia no existe. Idempotente: si
     * ya hay fila para ese par -hoy imposible desde {@link #crear}, ya que el
     * punto acaba de nacer, pero esta guarda se mantiene por si el metodo se
     * reutiliza mas adelante- no la toca, no la resetea, no la sobrescribe.
     * Nunca recibe aqui un ambiente distinto del configurado en el servidor:
     * quien llama siempre pasa {@link SriAmbienteProperties#getAmbiente()}.
     */
    private void asegurarSecuencialInicial(PuntoEmision punto, AmbienteSri ambiente) {
        if (secuencialEmisionRepository.findByPuntoEmision_IdAndAmbiente(punto.getId(), ambiente).isPresent()) {
            return;
        }
        SecuencialEmision secuencial = SecuencialEmision.builder()
                .puntoEmision(punto)
                .ambiente(ambiente)
                .ultimoSecuencial(0L)
                .build();
        secuencialEmisionRepository.save(secuencial);
    }

    private PuntoEmision puntoPorId(Long id) {
        return puntoEmisionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el punto de emision con id " + id + "."));
    }

    private Usuario usuarioActual(String email) {
        return usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + email));
    }

    private static String blankToNull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private static PuntoEmisionResponse toResponse(PuntoEmision p) {
        return new PuntoEmisionResponse(
                p.getId(), p.getEmisorFiscal().getId(), p.getEstablecimiento(), p.getPuntoEmision(),
                p.getDireccionEstablecimiento(), p.isActivo());
    }
}
