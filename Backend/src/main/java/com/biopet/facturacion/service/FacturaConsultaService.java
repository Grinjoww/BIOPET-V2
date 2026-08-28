package com.biopet.facturacion.service;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.dto.FacturaDetalleResponse;
import com.biopet.facturacion.dto.FacturaEventoSriResponse;
import com.biopet.facturacion.dto.FacturaPagoResponse;
import com.biopet.facturacion.dto.FacturaResponse;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDetalle;
import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.FacturaEventoSri;
import com.biopet.facturacion.entity.FacturaPago;
import com.biopet.facturacion.entity.OrigenDetalleFactura;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.repository.FacturaDocumentoRepository;
import com.biopet.facturacion.repository.FacturaEventoSriRepository;
import com.biopet.facturacion.repository.FacturaRepository;
import com.biopet.repository.UsuarioRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Lecturas de facturas orientadas a REST: listado, detalle, descarga de
 * documentos y bitacora SRI, todas con la comprobacion de propiedad de DUENO
 * aplicada aqui -en el backend, con datos reales de la fila- y no solo con
 * {@code @PreAuthorize} por rol.
 *
 * <h2>Por que es un servicio nuevo y no un metodo mas en los existentes</h2>
 *
 * <p>Ninguno de los cinco servicios del pipeline
 * ({@link FacturaBorradorService}, {@link FacturaEmisionService},
 * {@link FacturaXmlService}, {@link FacturaFirmaService},
 * {@link FacturaSriService}) tenia -ni necesitaba hasta ahora- una nocion de
 * "quien pregunta" ni de "como se ve esto desde fuera". Meter aqui el mapeo a
 * DTO y las reglas de visibilidad de la Fase 8A los habria hecho depender de
 * conceptos HTTP/seguridad que no les pertenecen. Este servicio SOLO lee: no
 * escribe nada, no reordena numeracion, no llama al SRI.
 *
 * <h2>Regla de visibilidad de DUENO</h2>
 *
 * <p>Preferencia conservadora de la fase: un DUENO solo ve una factura -en
 * listado o en detalle- cuando esta AUTORIZADA, y solo puede descargar
 * {@link TipoDocumentoFactura#XML_AUTORIZADO}. Ni un BORRADOR, ni una EMITIDA
 * en curso, ni una RECHAZADA, ni los documentos intermedios. La comprobacion
 * de propiedad ({@code factura.usuario.id == usuarioAutenticado.id}) y la de
 * visibilidad (estado AUTORIZADA) se hacen SIEMPRE con la fila real leida de
 * la base, nunca confiando en lo que el cliente afirme en la URL: un id ajeno
 * termina en {@link AccessDeniedException} (403), nunca en datos filtrados a
 * medias.
 *
 * <h2>Regla de visibilidad de VETERINARIO</h2>
 *
 * <p>Corregida en un pase posterior de la Fase 8A: un VETERINARIO NO tiene
 * lectura global de facturacion -a diferencia de como {@code CitaService} trata
 * a este mismo rol para citas-. Los datos fiscales son mas sensibles que un
 * agendamiento, asi que solo ve facturas con al menos una linea cuyo origen
 * clinico ({@link FacturaDetalle#getOrigenTipo()} /
 * {@link FacturaDetalle#getOrigenId()}) sea una Consulta o una Cita ASIGNADA A
 * el. La consulta que decide esto vive en
 * {@link FacturaRepository#buscarRelacionadasConVeterinario} y
 * {@link FacturaRepository#existeRelacionConVeterinario} -ver alli por que
 * solo esos dos origenes-. No tiene acceso a descarga de documentos ni a la
 * bitacora SRI en absoluto (restringido por {@code @PreAuthorize} en el
 * controlador, sin necesidad de comprobacion adicional aqui).
 */
@Service
public class FacturaConsultaService {

    private static final Logger log = LoggerFactory.getLogger(FacturaConsultaService.class);

    private final FacturaRepository facturaRepository;
    private final FacturaDocumentoRepository facturaDocumentoRepository;
    private final FacturaEventoSriRepository facturaEventoSriRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;

    public FacturaConsultaService(FacturaRepository facturaRepository,
                                  FacturaDocumentoRepository facturaDocumentoRepository,
                                  FacturaEventoSriRepository facturaEventoSriRepository,
                                  UsuarioRepository usuarioRepository,
                                  ObjectMapper objectMapper) {
        this.facturaRepository = facturaRepository;
        this.facturaDocumentoRepository = facturaDocumentoRepository;
        this.facturaEventoSriRepository = facturaEventoSriRepository;
        this.usuarioRepository = usuarioRepository;
        this.objectMapper = objectMapper;
    }

    // ==================================================================
    // Listado y detalle
    // ==================================================================

    /**
     * Listado paginado con filtros simples opcionales.
     *
     * <p>Si quien pregunta es DUENO, los filtros de {@code estado} y
     * {@code usuarioId} que llegasen del cliente se IGNORAN y se sustituyen:
     * {@code usuarioId} pasa a ser el suyo propio y {@code estado} pasa a ser
     * siempre AUTORIZADA. No es un valor por defecto que el cliente pueda
     * relajar pidiendo otra cosa; es la unica vista que un DUENO tiene de
     * "listar facturas".
     *
     * <p>Si quien pregunta es VETERINARIO, se usa una consulta distinta
     * ({@link FacturaRepository#buscarRelacionadasConVeterinario}) que ya
     * viene acotada a sus facturas relacionadas; el filtro {@code usuarioId}
     * que llegase del cliente se ignora (no tiene sentido: lo que acota aqui
     * es el ORIGEN clinico, no el propietario funcional de la factura).
     */
    @Transactional(readOnly = true)
    public Page<FacturaResponse> listar(EstadoFactura estado, Long usuarioId, Long mascotaId,
                                        LocalDate fechaEmision, Pageable pageable, String email) {
        Usuario usuario = usuarioActual(email);

        if (usuario.getRol() == Rol.ROLE_DUENO) {
            Page<Factura> pagina = facturaRepository.buscar(
                    EstadoFactura.AUTORIZADA, usuario.getId(), mascotaId, fechaEmision, pageable);
            return pagina.map(f -> toResponse(f, usuario.getRol()));
        }

        if (usuario.getRol() == Rol.ROLE_VETERINARIO) {
            Page<Factura> pagina = facturaRepository.buscarRelacionadasConVeterinario(
                    estado, mascotaId, fechaEmision, usuario.getId(),
                    OrigenDetalleFactura.CONSULTA, OrigenDetalleFactura.CITA, pageable);
            return pagina.map(f -> toResponse(f, usuario.getRol()));
        }

        Page<Factura> pagina = facturaRepository.buscar(estado, usuarioId, mascotaId, fechaEmision, pageable);
        return pagina.map(f -> toResponse(f, usuario.getRol()));
    }

    /**
     * Detalle de una factura.
     *
     * @throws com.biopet.exception.RecursoNoEncontradoException si no existe
     *         -404-.
     * @throws AccessDeniedException si es DUENO y la factura no es suya, o es
     *         suya pero no esta AUTORIZADA; o si es VETERINARIO y la factura no
     *         tiene ninguna linea con origen en una Consulta/Cita asignada a
     *         el -403 en ambos casos, nunca 404: no hay que dar pistas de que
     *         un id existe filtrando el codigo de estado o el motivo-.
     */
    @Transactional(readOnly = true)
    public FacturaResponse buscar(Long facturaId, String email) {
        Usuario usuario = usuarioActual(email);
        Factura factura = facturaPorId(facturaId);

        if (usuario.getRol() == Rol.ROLE_DUENO) {
            exigirVisibleParaDueno(usuario, factura);
        } else if (usuario.getRol() == Rol.ROLE_VETERINARIO) {
            exigirRelacionadaConVeterinario(usuario, factura);
        }

        return toResponse(factura, usuario.getRol());
    }

    /**
     * Igual que {@link #buscar}, pero SIN ningun control de acceso adicional:
     * relee la factura fresca -para que sus colecciones LAZY queden bien
     * inicializadas al mapear, ver el javadoc de {@code FacturaController}
     * sobre por que se relee en vez de mapear la entidad que devuelve el
     * servicio de escritura- y la convierte a DTO sin mirar quien pregunta.
     *
     * <p>Uso EXCLUSIVO de las respuestas de los endpoints de ESCRITURA (crear,
     * actualizar cabecera, comprador, detalles, pagos, emitir, generar-xml,
     * firmar, enviar-sri, sincronizar-sri). Todos esos endpoints estan
     * restringidos por {@code @PreAuthorize} a ADMIN/AUXILIAR -roles GLOBALES,
     * sin restriccion por objeto sobre facturas-, asi que el propio
     * {@code @PreAuthorize} YA es toda la autorizacion que hace falta: no hay
     * ningun ownership o relacion pendiente de comprobar antes de construir la
     * respuesta.
     *
     * <p>CRITICO: este metodo NUNCA debe usarse para responder una escritura
     * alcanzable por un rol con restriccion POR OBJETO (por ejemplo,
     * VETERINARIO, si algun dia recuperase algun endpoint de escritura, o
     * DUENO). Saltarse el gate de lectura solo es seguro cuando la
     * autorizacion de escritura por objeto ya fue comprobada ANTES de llegar
     * aqui; para un rol global esa comprobacion es trivial (el rol basta), pero
     * para un rol con ownership real haria falta repetir esa comprobacion
     * explicitamente antes de llamar a este metodo, cosa que hoy NO se hace.
     * VETERINARIO fue evaluado para escritura de borradores y se descarto: ver
     * "Por que VETERINARIO no escribe borradores" en {@code FacturaController}.
     */
    @Transactional(readOnly = true)
    public FacturaResponse mapearParaRespuestaDeEscritura(Long facturaId) {
        Factura factura = facturaPorId(facturaId);
        return toResponse(factura, null);
    }

    // ==================================================================
    // Documentos
    // ==================================================================

    /** Lo que hace falta para responder una descarga: bytes exactos y nombre de archivo. */
    public record DocumentoDescarga(byte[] contenido, String nombreArchivo) {
    }

    /**
     * Recupera un documento YA GENERADO, tal cual esta persistido. Nunca lo
     * regenera: si no existe (p. ej. se pide XML_FIRMADO antes de firmar), es
     * un 404, no un disparador para crearlo sobre la marcha.
     *
     * <p>DUENO: solo {@link TipoDocumentoFactura#XML_AUTORIZADO}, y solo de su
     * propia factura AUTORIZADA. Pedir cualquier otro tipo es 403 ANTES de
     * tocar la base de datos del documento -no hace falta ni comprobar si
     * existe para saber que no se va a entregar-.
     */
    @Transactional(readOnly = true)
    public DocumentoDescarga documento(Long facturaId, TipoDocumentoFactura tipo, String email) {
        Usuario usuario = usuarioActual(email);
        Factura factura = facturaPorId(facturaId);

        if (usuario.getRol() == Rol.ROLE_DUENO) {
            if (tipo != TipoDocumentoFactura.XML_AUTORIZADO) {
                throw new AccessDeniedException(
                        "Solo puede descargar el comprobante autorizado de sus propias facturas.");
            }
            exigirVisibleParaDueno(usuario, factura);
        }

        FacturaDocumento documento = facturaDocumentoRepository
                .findByFactura_IdAndTipo(facturaId, tipo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La factura " + facturaId + " todavia no tiene un documento " + tipo + "."));

        String nombre = nombreArchivo(factura, tipo);
        return new DocumentoDescarga(documento.getContenido(), nombre);
    }

    private static String nombreArchivo(Factura factura, TipoDocumentoFactura tipo) {
        String base = factura.getClaveAcceso() != null ? factura.getClaveAcceso()
                : "factura-" + factura.getId();
        return base + "-" + tipo.name().toLowerCase(java.util.Locale.ROOT) + ".xml";
    }

    // ==================================================================
    // Bitacora SRI
    // ==================================================================

    /**
     * Bitacora de intentos contra el SRI, del mas reciente al mas antiguo.
     * Endpoint restringido a ADMIN/AUXILIAR por {@code @PreAuthorize} en el
     * controlador (es informacion tecnica de diagnostico, no de cara al
     * cliente final), asi que aqui solo se comprueba que la factura exista.
     */
    @Transactional(readOnly = true)
    public List<FacturaEventoSriResponse> eventosSri(Long facturaId) {
        facturaPorId(facturaId);
        return facturaEventoSriRepository.findAllByFactura_IdOrderByCreadoEnDesc(facturaId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ==================================================================
    // Apoyo
    // ==================================================================

    private Usuario usuarioActual(String email) {
        return usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + email));
    }

    private Factura facturaPorId(Long facturaId) {
        if (facturaId == null) {
            throw new IllegalArgumentException("El identificador de la factura es obligatorio.");
        }
        return facturaRepository.findById(facturaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la factura con id " + facturaId + "."));
    }

    /**
     * Un unico mensaje para "no es suya" y para "es suya pero no visible
     * todavia": distinguirlos en la respuesta le diria a un DUENO curioso que
     * un id ajeno SI existe, o en que estado interno esta su propia factura
     * antes de tiempo.
     */
    private void exigirVisibleParaDueno(Usuario usuario, Factura factura) {
        boolean esSuya = factura.getUsuario() != null && factura.getUsuario().getId().equals(usuario.getId());
        boolean autorizada = factura.getEstado() == EstadoFactura.AUTORIZADA;
        if (!esSuya || !autorizada) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta factura.");
        }
    }

    /**
     * Un VETERINARIO solo ve una factura si al menos una de sus lineas tiene
     * origen en una Consulta o una Cita ASIGNADA A EL. Sin estado ni ownership
     * de por medio: una factura ya AUTORIZADA de un paciente que nunca atendio
     * sigue sin ser suya.
     */
    private void exigirRelacionadaConVeterinario(Usuario usuario, Factura factura) {
        boolean relacionada = facturaRepository.existeRelacionConVeterinario(
                factura.getId(), usuario.getId(),
                OrigenDetalleFactura.CONSULTA, OrigenDetalleFactura.CITA);
        if (!relacionada) {
            throw new AccessDeniedException("No tiene permisos para acceder a esta factura.");
        }
    }

    private FacturaResponse toResponse(Factura f, Rol rolConsultante) {
        List<TipoDocumentoFactura> documentos = facturaDocumentoRepository
                .findAllByFactura_Id(f.getId()).stream()
                .map(FacturaDocumento::getTipo)
                // Un DUENO no necesita saber que existen documentos intermedios
                // que nunca podra descargar; solo le importa el autorizado.
                .filter(tipo -> rolConsultante != Rol.ROLE_DUENO || tipo == TipoDocumentoFactura.XML_AUTORIZADO)
                .toList();

        return new FacturaResponse(
                f.getId(),
                f.getEstado(),
                f.getUsuario() != null ? f.getUsuario().getId() : null,

                f.getAmbiente(),
                f.getEstablecimiento(),
                f.getPuntoEmisionCodigo(),
                f.getSecuencial(),
                f.getCodigoNumerico(),
                f.getClaveAcceso(),
                f.getFechaEmision(),

                f.getCompradorTipoIdentificacion(),
                f.getCompradorIdentificacion(),
                f.getCompradorRazonSocial(),
                f.getCompradorDireccion(),
                f.getCompradorEmail(),
                f.getCompradorTelefono(),

                f.getMascota() != null ? f.getMascota().getId() : null,
                f.getMascota() != null ? f.getMascota().getNombre() : null,

                f.getDetalles().stream().sorted(java.util.Comparator.comparing(FacturaDetalle::getLinea))
                        .map(this::toResponse).toList(),
                f.getPagos().stream().map(this::toResponse).toList(),

                f.getTotalSinImpuestos(),
                f.getTotalDescuento(),
                f.getTotalImpuestos(),
                f.getImporteTotal(),
                f.getMoneda(),

                f.getEstadoRecepcion(),
                f.getEstadoAutorizacion(),
                f.getNumeroAutorizacion(),
                f.getFechaAutorizacion(),
                f.getProximoIntentoEn(),
                f.getIntentosAutorizacion(),

                documentos,

                f.getCreadoEn(),
                f.getActualizadoEn());
    }

    private FacturaDetalleResponse toResponse(FacturaDetalle d) {
        return new FacturaDetalleResponse(
                d.getLinea(),
                d.getConceptoFacturable() != null ? d.getConceptoFacturable().getId() : null,
                d.getCodigoPrincipal(),
                d.getDescripcion(),
                d.getCantidad(),
                d.getPrecioUnitario(),
                d.getDescuento(),
                d.getPrecioTotalSinImpuesto(),
                d.getImpuestoCodigo(),
                d.getImpuestoCodigoPorcentaje(),
                d.getImpuestoTarifa(),
                d.getBaseImponible(),
                d.getImpuestoValor(),
                d.getOrigenTipo(),
                d.getOrigenId());
    }

    private FacturaPagoResponse toResponse(FacturaPago p) {
        return new FacturaPagoResponse(p.getFormaPago(), p.getTotal(), p.getPlazo(), p.getUnidadTiempo());
    }

    private FacturaEventoSriResponse toResponse(FacturaEventoSri e) {
        return new FacturaEventoSriResponse(
                e.getId(),
                e.getOperacion(),
                e.getResultado(),
                aJsonNode(e.getMensajes()),
                e.getDuracionMs(),
                e.getIntento(),
                e.getCreadoEn());
    }

    /**
     * La columna es JSONB y llega como {@code String} (ver
     * {@code FacturaEventoSri}); se reparsea para que Jackson la serialice
     * como JSON de verdad y no como una cadena escapada dos veces. Un evento
     * sin cuerpo (p. ej. un TIMEOUT) tiene {@code mensajes == null} y no hay
     * nada que parsear.
     */
    private JsonNode aJsonNode(String mensajesJson) {
        if (mensajesJson == null || mensajesJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(mensajesJson);
        } catch (JsonProcessingException e) {
            log.error("La bitacora SRI contiene un JSON que no se pudo re-parsear.", e);
            return null;
        }
    }
}
