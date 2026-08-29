package com.biopet.controller;

import com.biopet.facturacion.dto.ActualizarFacturaRequest;
import com.biopet.facturacion.dto.CrearFacturaRequest;
import com.biopet.facturacion.dto.DetalleFacturaRequest;
import com.biopet.facturacion.dto.EmitirFacturaRequest;
import com.biopet.facturacion.dto.FacturaEventoSriResponse;
import com.biopet.facturacion.dto.FacturaResponse;
import com.biopet.facturacion.dto.PagoFacturaRequest;
import com.biopet.facturacion.dto.ReemplazarDetallesRequest;
import com.biopet.facturacion.dto.ReemplazarPagosRequest;
import com.biopet.facturacion.dto.SeleccionarCompradorRequest;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.service.FacturaBorradorService;
import com.biopet.facturacion.service.FacturaConsultaService;
import com.biopet.facturacion.service.FacturaConsultaService.DocumentoDescarga;
import com.biopet.facturacion.service.FacturaEmisionService;
import com.biopet.facturacion.service.FacturaFirmaService;
import com.biopet.facturacion.service.FacturaRideService;
import com.biopet.facturacion.service.FacturaSriService;
import com.biopet.facturacion.service.FacturaXmlService;
import com.biopet.facturacion.service.command.ActualizarFacturaBorradorCommand;
import com.biopet.facturacion.service.command.CrearFacturaBorradorCommand;
import com.biopet.facturacion.service.command.DetalleBorradorCommand;
import com.biopet.facturacion.service.command.EmitirFacturaCommand;
import com.biopet.facturacion.service.command.PagoBorradorCommand;
import com.biopet.facturacion.sri.SriAmbienteProperties;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * API REST del pipeline fiscal: borrador -&gt; emision -&gt; XML -&gt; firma -&gt;
 * recepcion/autorizacion SRI, mas consulta, descarga de documentos y bitacora.
 *
 * <h2>Sin logica fiscal aqui</h2>
 *
 * <p>Este controlador NO decide nada: cada metodo valida forma (Bean
 * Validation + el path variable), resuelve el usuario autenticado y delega en
 * el servicio de dominio correspondiente. Numeracion, calculo de impuestos,
 * validacion de XSD, verificacion de firma y el dialogo SOAP con el SRI viven,
 * sin excepcion, en los servicios de las Fases 4-7
 * ({@link FacturaBorradorService}, {@link FacturaEmisionService},
 * {@link FacturaXmlService}, {@link FacturaFirmaService},
 * {@link FacturaSriService}) y en {@link FacturaConsultaService} para las
 * lecturas. Repetir aqui cualquiera de esas reglas seria la segunda fuente de
 * verdad que la Fase 8A tiene explicitamente prohibida.
 *
 * <h2>La respuesta de cada escritura se relee, no se reconstruye a mano</h2>
 *
 * <p>Tras cada operacion de escritura se llama a
 * {@link FacturaConsultaService#mapearParaRespuestaDeEscritura}, que abre su
 * PROPIA transaccion de solo lectura y devuelve el DTO ya completo. Es
 * deliberado y no un descuido de rendimiento: los servicios de escritura
 * devuelven la entidad JPA tal como queda al COMMIT de su propia transaccion,
 * con colecciones LAZY que pueden no estar inicializadas; intentar mapearla a
 * DTO ya fuera de esa transaccion arriesgaria un
 * {@code LazyInitializationException} dependiendo de que haya tocado cada
 * operacion internamente. Releer con una consulta fresca cuesta un SELECT mas
 * por peticion y evita ese acoplamiento fragil por completo.
 *
 * <p>Es una consulta DISTINTA de {@link FacturaConsultaService#buscar} -la que
 * usa {@code GET /{id}}- y a proposito: esta NO aplica ningun filtro de
 * ownership/relacion. Es SEGURO que no lo aplique porque, a dia de hoy, todo
 * rol que llega a un endpoint de escritura ({@code ADMIN}/{@code AUXILIAR}) es
 * un rol GLOBAL sin restriccion por objeto: el {@code @PreAuthorize} de esos
 * metodos YA es toda la autorizacion que hace falta. Este metodo NUNCA debe
 * usarse para responder una escritura hecha por un rol que si tenga
 * restriccion por objeto (ver la nota de VETERINARIO mas abajo): saltarse el
 * gate de lectura solo es seguro cuando la autorizacion de escritura por
 * objeto ya se comprobo antes de llegar aqui -y, para un rol global, "ya se
 * comprobo" es literalmente "el rol es global, no hay nada mas que
 * comprobar"-.
 *
 * <h2>Permisos</h2>
 *
 * <p>Matriz aplicada con {@code @PreAuthorize} (rol "de fábrica") y, para
 * DUENO, con una comprobacion adicional de PROPIEDAD hecha en
 * {@link FacturaConsultaService} contra la fila real leida de la base -nunca
 * solo confiando en el rol-. Ver el javadoc de esa clase para el detalle de las
 * reglas de visibilidad de DUENO (solo AUTORIZADA, solo XML_AUTORIZADO) y de
 * VETERINARIO (solo facturas con una linea originada en una Consulta/Cita
 * asignada a el; NO es lectura global, a diferencia de como {@code CitaService}
 * trata a este mismo rol para citas -los datos fiscales son mas sensibles-).
 *
 * <ul>
 *   <li>ADMIN: acceso completo.</li>
 *   <li>AUXILIAR: borrador, emision, XML, firma, SRI, consulta y descarga.</li>
 *   <li>VETERINARIO: SOLO consulta de facturas clinicamente relacionadas con
 *       el (ver {@link FacturaConsultaService}). NO crea ni edita borradores
 *       -ver la nota "Por que VETERINARIO no escribe" mas abajo-, NUNCA emite,
 *       firma, envia ni sincroniza con el SRI, ni descarga documentos, ni ve
 *       la bitacora SRI.</li>
 *   <li>DUENO: solo consulta sus propias facturas AUTORIZADAS y descarga su
 *       propio XML_AUTORIZADO. No crea ni edita borradores: en el dominio
 *       actual una factura siempre se prepara desde la clinica.</li>
 * </ul>
 *
 * <h2>Por que VETERINARIO no escribe borradores</h2>
 *
 * <p>Se evaluo y se descarto. Un {@code Factura} BORRADOR no tiene ningun
 * campo que lo relacione con un veterinario: ni al crearse (nace vacio, sin
 * lineas), ni al fijar cabecera/comprador/pagos (esas operaciones no tocan
 * nada clinico). La UNICA relacion posible con un veterinario pasa por
 * {@code FacturaDetalle.origenTipo/origenId} apuntando a una Consulta/Cita
 * suya, y eso NO sirve como control de escritura: una factura puede tener
 * cero lineas con origen clinico (venta de producto), o lineas de VARIOS
 * veterinarios distintos a la vez, o cambiar de contenido por completo en
 * cada {@code PUT /detalles} (que REEMPLAZA todas las lineas). No existe una
 * relacion inequivoca y verificable que proteja "este borrador es de este
 * veterinario" en ningun punto de la escritura. Ante esa ausencia se aplica la
 * opcion conservadora: VETERINARIO no escribe, solo lee lo que ya demuestra
 * estar clinicamente relacionado con el.
 */
@RestController
@RequestMapping("/api/facturas")
public class FacturaController {

    private final FacturaBorradorService borradorService;
    private final FacturaEmisionService emisionService;
    private final FacturaXmlService xmlService;
    private final FacturaFirmaService firmaService;
    private final FacturaSriService sriService;
    private final FacturaConsultaService consultaService;
    private final FacturaRideService facturaRideService;
    private final SriAmbienteProperties ambienteProperties;

    public FacturaController(FacturaBorradorService borradorService,
                             FacturaEmisionService emisionService,
                             FacturaXmlService xmlService,
                             FacturaFirmaService firmaService,
                             FacturaSriService sriService,
                             FacturaConsultaService consultaService,
                             FacturaRideService facturaRideService,
                             SriAmbienteProperties ambienteProperties) {
        this.borradorService = borradorService;
        this.emisionService = emisionService;
        this.xmlService = xmlService;
        this.firmaService = firmaService;
        this.sriService = sriService;
        this.consultaService = consultaService;
        this.facturaRideService = facturaRideService;
        this.ambienteProperties = ambienteProperties;
    }

    // ==================================================================
    // Consulta
    // ==================================================================

    /**
     * Filtros simples y opcionales (estado, usuario, mascota, fecha exacta de
     * emision), nunca un motor de busqueda. Para DUENO, {@code estado} y
     * {@code usuarioId} se sustituyen SIEMPRE por AUTORIZADA y su propio id,
     * ignorando lo que el cliente haya mandado: ver
     * {@link FacturaConsultaService#listar}.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','VETERINARIO','DUENO')")
    public Page<FacturaResponse> listar(@RequestParam(required = false) EstadoFactura estado,
                                        @RequestParam(required = false) Long usuarioId,
                                        @RequestParam(required = false) Long mascotaId,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEmision,
                                        Pageable pageable,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        return consultaService.listar(estado, usuarioId, mascotaId, fechaEmision, pageable,
                userDetails.getUsername());
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','VETERINARIO','DUENO')")
    public FacturaResponse buscar(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        return consultaService.buscar(id, userDetails.getUsername());
    }

    // ==================================================================
    // Borrador
    // ==================================================================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public ResponseEntity<FacturaResponse> crear(@Valid @RequestBody CrearFacturaRequest request) {
        Factura borrador = borradorService.crear(new CrearFacturaBorradorCommand(
                request.usuarioId(), request.mascotaId(), request.fechaEmision()));
        FacturaResponse creado = consultaService.mapearParaRespuestaDeEscritura(borrador.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public FacturaResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarFacturaRequest request) {
        borradorService.actualizar(id,
                new ActualizarFacturaBorradorCommand(request.mascotaId(), request.fechaEmision()));
        return consultaService.mapearParaRespuestaDeEscritura(id);
    }

    @PostMapping("/{id:\\d+}/comprador")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public FacturaResponse seleccionarComprador(@PathVariable Long id,
                                                @Valid @RequestBody SeleccionarCompradorRequest request) {
        borradorService.seleccionarComprador(id, request.datosFacturacionId());
        return consultaService.mapearParaRespuestaDeEscritura(id);
    }

    @PutMapping("/{id:\\d+}/detalles")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public FacturaResponse reemplazarDetalles(@PathVariable Long id,
                                              @Valid @RequestBody ReemplazarDetallesRequest request) {
        List<DetalleBorradorCommand> comandos = request.detalles().stream()
                .map(FacturaController::aComando)
                .toList();
        borradorService.reemplazarDetalles(id, comandos);
        return consultaService.mapearParaRespuestaDeEscritura(id);
    }

    @PutMapping("/{id:\\d+}/pagos")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public FacturaResponse reemplazarPagos(@PathVariable Long id,
                                           @Valid @RequestBody ReemplazarPagosRequest request) {
        List<PagoBorradorCommand> comandos = request.pagos().stream()
                .map(FacturaController::aComando)
                .toList();
        borradorService.reemplazarPagos(id, comandos);
        return consultaService.mapearParaRespuestaDeEscritura(id);
    }

    // ==================================================================
    // Pipeline fiscal: emision, XML, firma, SRI
    // ==================================================================

    @PostMapping("/{id:\\d+}/emitir")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public FacturaResponse emitir(@PathVariable Long id, @Valid @RequestBody EmitirFacturaRequest request) {
        // El ambiente NUNCA sale del request: lo resuelve el backend.
        emisionService.emitir(new EmitirFacturaCommand(
                id, request.puntoEmisionId(), ambienteProperties.getAmbiente()));
        return consultaService.mapearParaRespuestaDeEscritura(id);
    }

    @PostMapping("/{id:\\d+}/generar-xml")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public FacturaResponse generarXml(@PathVariable Long id) {
        xmlService.generarXml(id);
        return consultaService.mapearParaRespuestaDeEscritura(id);
    }

    @PostMapping("/{id:\\d+}/firmar")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public FacturaResponse firmar(@PathVariable Long id) {
        firmaService.firmarFactura(id);
        return consultaService.mapearParaRespuestaDeEscritura(id);
    }

    /**
     * No hay sleeps ni polling HTTP aqui: esta llamada hace UNA consulta o
     * envio sincrono al SRI (ver {@link FacturaSriService#enviar}) y devuelve.
     * Si el SRI queda en PPR, la factura sigue EMITIDA con
     * {@code proximoIntentoEn} informado, y el cliente decide cuando volver a
     * llamar a {@code /sincronizar-sri}.
     */
    @PostMapping("/{id:\\d+}/enviar-sri")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public FacturaResponse enviarSri(@PathVariable Long id) {
        sriService.enviar(id);
        return consultaService.mapearParaRespuestaDeEscritura(id);
    }

    @PostMapping("/{id:\\d+}/sincronizar-sri")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public FacturaResponse sincronizarSri(@PathVariable Long id) {
        sriService.sincronizar(id);
        return consultaService.mapearParaRespuestaDeEscritura(id);
    }

    // ==================================================================
    // Documentos y bitacora
    // ==================================================================

    /**
     * Descarga los bytes EXACTOS ya persistidos, nunca regenerados. El tipo va
     * en el path como el enum real ({@link TipoDocumentoFactura}): un valor
     * que no sea uno de sus literales ni siquiera llega a este metodo -Spring
     * lo rechaza al convertir el path variable, y eso ya cae en el manejador
     * existente de {@code MethodArgumentTypeMismatchException} (400)-, asi que
     * no hace falta validar aqui un "tipo arbitrario" a mano.
     */
    @GetMapping("/{id:\\d+}/documentos/{tipo}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','DUENO')")
    public ResponseEntity<byte[]> documento(@PathVariable Long id, @PathVariable TipoDocumentoFactura tipo,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        DocumentoDescarga descarga = consultaService.documento(id, tipo, userDetails.getUsername());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + descarga.nombreArchivo() + "\"")
                .body(descarga.contenido());
    }

    /**
     * RIDE (representacion impresa) de una factura AUTORIZADA (Fase 10).
     *
     * <p>A diferencia de {@link #documento}, este endpoint SI puede generar
     * contenido nuevo -es la unica escritura implicita detras de un
     * {@code GET} en todo este controlador, y es deliberada: el RIDE es la
     * unica pieza de la Fase 10, no existe un paso previo tipo
     * "/generar-ride" analogo a {@code /generar-xml}/{@code /firmar}, y la
     * generacion es local (PDF a partir de datos ya persistidos, sin tocar
     * el SRI) e idempotente-. La primera llamada genera y persiste; las
     * siguientes devuelven los MISMOS bytes ya guardados
     * ({@link FacturaRideService#generarRide}).
     *
     * <p>El control de acceso se comprueba ANTES de generar nada
     * ({@link FacturaConsultaService#exigirAccesoADocumento}): un DUENO
     * pidiendo el RIDE de una factura ajena, o de una propia que todavia no
     * esta AUTORIZADA, recibe 403 sin que se gaste trabajo construyendo un
     * PDF que no se le iba a entregar.
     */
    @GetMapping("/{id:\\d+}/ride")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','DUENO')")
    public ResponseEntity<byte[]> ride(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        consultaService.exigirAccesoADocumento(id, TipoDocumentoFactura.RIDE_PDF, userDetails.getUsername());
        FacturaDocumento documento = facturaRideService.generarRide(id);
        String nombre = consultaService.nombreArchivoDocumento(id, TipoDocumentoFactura.RIDE_PDF);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(documento.getContenido());
    }

    /** Solo lectura, solo diagnostico tecnico: ADMIN/AUXILIAR, ver la clase. */
    @GetMapping("/{id:\\d+}/eventos-sri")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public List<FacturaEventoSriResponse> eventosSri(@PathVariable Long id) {
        return consultaService.eventosSri(id);
    }

    // ==================================================================
    // Mapeo de DTOs de entrada a los commands de dominio existentes
    // ==================================================================

    private static DetalleBorradorCommand aComando(DetalleFacturaRequest d) {
        return new DetalleBorradorCommand(
                d.conceptoFacturableId(), d.cantidad(), d.descuento(), d.origenTipo(), d.origenId());
    }

    private static PagoBorradorCommand aComando(PagoFacturaRequest p) {
        return new PagoBorradorCommand(p.formaPago(), p.total(), p.plazo(), p.unidadTiempo());
    }
}
