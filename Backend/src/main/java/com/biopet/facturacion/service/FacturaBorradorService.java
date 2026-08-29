package com.biopet.facturacion.service;

import com.biopet.entity.Mascota;
import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDetalle;
import com.biopet.facturacion.entity.FacturaPago;
import com.biopet.facturacion.exception.ConceptoFacturableNoDisponibleException;
import com.biopet.facturacion.exception.FacturaNoEditableException;
import com.biopet.facturacion.exception.TitularFacturaInvalidoException;
import com.biopet.facturacion.repository.ConceptoFacturableRepository;
import com.biopet.facturacion.repository.DatosFacturacionRepository;
import com.biopet.facturacion.repository.FacturaDetalleRepository;
import com.biopet.facturacion.repository.FacturaDocumentoRepository;
import com.biopet.facturacion.repository.FacturaPagoRepository;
import com.biopet.facturacion.repository.FacturaRepository;
import com.biopet.facturacion.service.command.ActualizarFacturaBorradorCommand;
import com.biopet.facturacion.service.command.CrearFacturaBorradorCommand;
import com.biopet.facturacion.service.command.DetalleBorradorCommand;
import com.biopet.facturacion.service.command.PagoBorradorCommand;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye y edita facturas mientras son BORRADOR.
 *
 * <p>Un borrador es un documento interno sin ningun valor fiscal: no tiene
 * secuencial, ni codigo numerico, ni clave de acceso, ni estado frente al SRI.
 * Puede guardarse incompleto y modificarse cuantas veces haga falta. Nada de lo
 * que ocurre aqui reserva numeracion: el contador solo se toca al emitir
 * ({@link FacturaEmisionService}).
 *
 * <p>Los importes que este servicio deja guardados son PROVISIONALES. Se
 * calculan con el precio y la tarifa vigentes en ese instante para que la futura
 * interfaz pueda mostrar un total, pero no son los definitivos: al emitir se
 * recalcula todo desde cero (mismo {@link FacturaCalculador}) y ese es el valor
 * que se congela.
 *
 * <p>Se separa de la emision a proposito. Son dos responsabilidades con perfiles
 * muy distintos: aqui hay muchas operaciones pequenas, tolerantes y repetibles;
 * alli hay una sola operacion critica, irreversible y con bloqueos. Juntarlas
 * daria una clase grande en la que la parte delicada quedaria escondida entre
 * setters.
 */
@Service
public class FacturaBorradorService {

    private final FacturaRepository facturaRepository;
    private final FacturaDetalleRepository facturaDetalleRepository;
    private final FacturaPagoRepository facturaPagoRepository;
    private final FacturaDocumentoRepository facturaDocumentoRepository;
    private final DatosFacturacionRepository datosFacturacionRepository;
    private final ConceptoFacturableRepository conceptoFacturableRepository;
    private final UsuarioRepository usuarioRepository;
    private final MascotaRepository mascotaRepository;
    private final OrigenClinicoValidator origenClinicoValidator;
    private final FacturaCalculador facturaCalculador;

    public FacturaBorradorService(FacturaRepository facturaRepository,
                                  FacturaDetalleRepository facturaDetalleRepository,
                                  FacturaPagoRepository facturaPagoRepository,
                                  FacturaDocumentoRepository facturaDocumentoRepository,
                                  DatosFacturacionRepository datosFacturacionRepository,
                                  ConceptoFacturableRepository conceptoFacturableRepository,
                                  UsuarioRepository usuarioRepository,
                                  MascotaRepository mascotaRepository,
                                  OrigenClinicoValidator origenClinicoValidator,
                                  FacturaCalculador facturaCalculador) {
        this.facturaRepository = facturaRepository;
        this.facturaDetalleRepository = facturaDetalleRepository;
        this.facturaPagoRepository = facturaPagoRepository;
        this.facturaDocumentoRepository = facturaDocumentoRepository;
        this.datosFacturacionRepository = datosFacturacionRepository;
        this.conceptoFacturableRepository = conceptoFacturableRepository;
        this.usuarioRepository = usuarioRepository;
        this.mascotaRepository = mascotaRepository;
        this.origenClinicoValidator = origenClinicoValidator;
        this.facturaCalculador = facturaCalculador;
    }

    // ==================================================================
    // Cabecera
    // ==================================================================

    /** Abre un borrador vacio: sin comprador, sin lineas y sin pagos. */
    @Transactional
    public Factura crear(CrearFacturaBorradorCommand comando) {
        if (comando == null || comando.usuarioId() == null) {
            throw new IllegalArgumentException("El usuario propietario de la factura es obligatorio.");
        }
        LocalDate fechaEmision = exigirFecha(comando.fechaEmision());

        Usuario usuario = usuarioActivo(comando.usuarioId());
        Mascota mascota = mascotaDelUsuario(comando.mascotaId(), usuario.getId());

        Factura borrador = Factura.builder()
                .usuario(usuario)
                .mascota(mascota)
                .fechaEmision(fechaEmision)
                .estado(EstadoFactura.BORRADOR)
                .build();

        return facturaRepository.save(borrador);
    }

    /**
     * Cambia mascota y fecha de emision.
     *
     * <p>Cambiar la fecha no es cosmetico: gobierna que tarifa se aplica, asi
     * que las lineas se recalculan. Cambiar (o quitar) la mascota obliga a
     * revalidar la trazabilidad clinica de todas las lineas, porque un origen
     * que era coherente con la mascota anterior deja de serlo.
     */
    @Transactional
    public Factura actualizar(Long facturaId, ActualizarFacturaBorradorCommand comando) {
        Factura factura = borradorEditable(facturaId);
        if (comando == null) {
            throw new IllegalArgumentException("Los datos de actualizacion son obligatorios.");
        }

        factura.setFechaEmision(exigirFecha(comando.fechaEmision()));
        factura.setMascota(mascotaDelUsuario(comando.mascotaId(), factura.getUsuario().getId()));

        for (FacturaDetalle detalle : factura.getDetalles()) {
            origenClinicoValidator.validar(
                    factura.getMascota(), detalle.getOrigenTipo(), detalle.getOrigenId());
        }
        facturaCalculador.recalcularYVolcar(factura);

        return facturaRepository.saveAndFlush(factura);
    }

    // ==================================================================
    // Comprador
    // ==================================================================

    /**
     * Copia una identidad tributaria del usuario al snapshot del borrador.
     *
     * <p>Se copia, no se referencia. La factura no guarda clave ajena hacia
     * {@code datos_facturacion}, y no es un olvido: lo que vale fiscalmente es
     * lo que el usuario eligio para ESTA factura. Si manana edita su direccion
     * en su libreta de datos, la factura no debe cambiar sola; para trasladar el
     * cambio debe volver a seleccionar los datos aqui, explicitamente.
     */
    @Transactional
    public Factura seleccionarComprador(Long facturaId, Long datosFacturacionId) {
        Factura factura = borradorEditable(facturaId);
        if (datosFacturacionId == null) {
            throw new IllegalArgumentException("Los datos de facturacion son obligatorios.");
        }

        Long usuarioId = factura.getUsuario().getId();
        DatosFacturacion datos = datosFacturacionRepository
                .findByIdAndUsuario_IdAndActivoTrue(datosFacturacionId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontraron datos de facturacion activos con id " + datosFacturacionId
                                + " para el usuario " + usuarioId + "."));

        factura.setCompradorTipoIdentificacion(datos.getTipoIdentificacion());
        factura.setCompradorIdentificacion(datos.getIdentificacion());
        factura.setCompradorRazonSocial(datos.getRazonSocial());
        factura.setCompradorDireccion(datos.getDireccion());
        factura.setCompradorEmail(datos.getEmailFacturacion());
        factura.setCompradorTelefono(datos.getTelefono());

        return facturaRepository.saveAndFlush(factura);
    }

    // ==================================================================
    // Lineas
    // ==================================================================

    /**
     * Sustituye por completo las lineas del borrador.
     *
     * <p>Se reemplaza en bloque en lugar de ofrecer alta/baja/modificacion linea
     * a linea porque la numeracion {@code linea} debe quedar contigua desde 1 y
     * porque cualquier cambio obliga a recalcular la factura entera de todos
     * modos (los impuestos se agrupan entre lineas).
     *
     * <p>El borrado de las lineas anteriores es EXPLICITO. La relacion se
     * declaro en la Fase 4A sin {@code orphanRemoval} a proposito, para que
     * ningun DELETE sobre un comprobante pueda dispararse por quitar un elemento
     * de una lista en memoria; aqui se pide el borrado a mano y solo tras
     * comprobar que la factura sigue siendo BORRADOR.
     */
    @Transactional
    public Factura reemplazarDetalles(Long facturaId, List<DetalleBorradorCommand> detalles) {
        Factura factura = borradorEditable(facturaId);
        List<DetalleBorradorCommand> comandos = detalles == null ? List.of() : detalles;

        borrarDetalles(factura);

        int numeroLinea = 1;
        for (DetalleBorradorCommand comando : comandos) {
            factura.agregarDetalle(nuevoDetalle(factura, comando, numeroLinea++));
        }

        // El calculo rellena precio, descripcion, impuesto y tarifa: el comando
        // solo aporto concepto, cantidad, descuento y trazabilidad.
        facturaCalculador.recalcularYVolcar(factura);

        return facturaRepository.saveAndFlush(factura);
    }

    private FacturaDetalle nuevoDetalle(Factura factura, DetalleBorradorCommand comando, int numeroLinea) {
        if (comando == null || comando.conceptoFacturableId() == null) {
            throw new IllegalArgumentException(
                    "Cada linea debe indicar un concepto facturable (linea " + numeroLinea + ").");
        }
        if (comando.cantidad() == null) {
            throw new IllegalArgumentException(
                    "La cantidad es obligatoria (linea " + numeroLinea + ").");
        }

        origenClinicoValidator.validar(factura.getMascota(), comando.origenTipo(), comando.origenId());
        ConceptoFacturable concepto = conceptoActivo(comando.conceptoFacturableId());

        // La linea nace copiada del CATALOGO, nunca del comando: este solo
        // aporta concepto, cantidad, descuento y trazabilidad. Los importes
        // calculados quedan a cero y los rellena FacturaCalculador un momento
        // despues, dentro de esta misma transaccion. No son valores inventados,
        // son el estado inicial de un total que aun no se ha calculado; poner
        // aqui un impuesto o una tarifa cualquiera si seria inventarselos.
        return FacturaDetalle.builder()
                .factura(factura)
                .linea(numeroLinea)
                .conceptoFacturable(concepto)
                .codigoPrincipal(concepto.getCodigo())
                .descripcion(concepto.getDescripcion())
                .cantidad(comando.cantidad())
                .precioUnitario(concepto.getPrecioUnitario())
                .descuento(comando.descuento() == null ? BigDecimal.ZERO : comando.descuento())
                .precioTotalSinImpuesto(BigDecimal.ZERO)
                .impuestoCodigo(concepto.getCodigoImpuesto())
                .impuestoCodigoPorcentaje(concepto.getCodigoPorcentaje())
                .impuestoTarifa(BigDecimal.ZERO)
                .baseImponible(BigDecimal.ZERO)
                .impuestoValor(BigDecimal.ZERO)
                .origenTipo(comando.origenTipo())
                .origenId(comando.origenId())
                .build();
    }

    /**
     * Carga el concepto exigiendo que este ACTIVO, para que anadir una linea con
     * un concepto retirado falle aqui -donde el mensaje senala la linea- y no
     * mas tarde dentro del calculo.
     */
    private ConceptoFacturable conceptoActivo(Long conceptoId) {
        return conceptoFacturableRepository.findByIdAndActivoTrue(conceptoId)
                .orElseThrow(() -> new ConceptoFacturableNoDisponibleException(conceptoId));
    }

    // ==================================================================
    // Pagos
    // ==================================================================

    /**
     * Sustituye por completo las formas de pago.
     *
     * <p>Aqui NO se exige todavia que sumen el importe total: un borrador a
     * medio armar debe poder guardarse con un pago incompleto. Esa igualdad se
     * comprueba al emitir, que es cuando el documento tiene que cuadrar.
     */
    @Transactional
    public Factura reemplazarPagos(Long facturaId, List<PagoBorradorCommand> pagos) {
        Factura factura = borradorEditable(facturaId);
        List<PagoBorradorCommand> comandos = pagos == null ? List.of() : pagos;

        borrarPagos(factura);

        for (PagoBorradorCommand comando : comandos) {
            if (comando == null || comando.formaPago() == null || comando.total() == null) {
                throw new IllegalArgumentException(
                        "Cada forma de pago debe indicar codigo e importe.");
            }
            factura.agregarPago(FacturaPago.builder()
                    .factura(factura)
                    .formaPago(comando.formaPago())
                    .total(comando.total())
                    .plazo(comando.plazo())
                    .unidadTiempo(comando.unidadTiempo())
                    .build());
        }

        return facturaRepository.saveAndFlush(factura);
    }

    // ==================================================================
    // Eliminacion
    // ==================================================================

    /**
     * Borra FISICAMENTE un borrador: la unica factura que puede desaparecer de
     * verdad, porque nunca consumio numeracion fiscal (ver el javadoc de
     * {@link Factura} sobre por que no existe baja logica para el resto de
     * estados). No es "anular" ni "cancelar" -eso son operaciones del pipeline
     * SRI, todavia sin implementar-; es descartar un documento interno a medio
     * armar que nadie mas que BIOPET conoce.
     *
     * <h2>Doble candado antes de borrar</h2>
     *
     * <p>1) {@code estado == BORRADOR} ({@link #borradorEditable}, el mismo
     * candado que ya protege editar/reemplazar detalles y pagos). 2) Ademas,
     * defensivamente, que NINGUN campo de numeracion/autorizacion este
     * relleno y que no exista ni un solo {@code FacturaDocumento} archivado
     * ({@link #exigirSinRastroFiscal}). El primer candado deberia bastar -la
     * maquina de estados actual no permite que un BORRADOR tenga clave de
     * acceso-, pero un DELETE fisico es irreversible: se prefiere fallar en
     * un caso que "no deberia poder pasar" antes que arriesgar borrar un
     * comprobante con algun rastro fiscal por una inconsistencia de datos que
     * esta fase no previo.
     *
     * <h2>Hijos</h2>
     *
     * <p>{@code factura_detalles} y {@code factura_pagos} se borran EXPLICITAMENTE
     * antes que la cabecera, reutilizando {@link #borrarDetalles}/
     * {@link #borrarPagos} -los mismos metodos que ya usa
     * {@link #reemplazarDetalles}/{@link #reemplazarPagos}-: ninguna FK de V8
     * lleva {@code ON DELETE CASCADE} (decision deliberada de esa migracion),
     * asi que sin este paso el {@code DELETE} de la cabecera fallaria por
     * integridad referencial. Nunca se toca {@code ConceptoFacturable},
     * {@code DatosFacturacion}, {@code Mascota}, {@code Usuario} ni
     * {@code PuntoEmision}: son entidades compartidas, solo trazabilidad
     * desde el borrador, jamas de su propiedad.
     *
     * @throws com.biopet.exception.RecursoNoEncontradoException si no existe (404).
     * @throws FacturaNoEditableException si no esta en BORRADOR (409).
     */
    @Transactional
    public void eliminar(Long facturaId) {
        Factura factura = borradorEditable(facturaId);
        exigirSinRastroFiscal(factura);

        borrarPagos(factura);
        borrarDetalles(factura);
        facturaRepository.delete(factura);
    }

    /**
     * Defensa adicional descrita en el javadoc de {@link #eliminar}: ademas
     * del estado, ningun campo que solo se rellena al emitir puede estar
     * presente, y no debe existir ningun documento archivado (XML en
     * cualquiera de sus tres formas, o RIDE). Reutiliza
     * {@link FacturaNoEditableException} -el mismo mensaje "solo puede
     * modificarse mientras sea BORRADOR" describe exactamente este caso,
     * borrar es la forma mas fuerte de modificar- en vez de anadir una
     * excepcion nueva para un camino que la maquina de estados actual no deja
     * alcanzar.
     */
    private void exigirSinRastroFiscal(Factura factura) {
        boolean tieneNumeracion = factura.getClaveAcceso() != null
                || factura.getSecuencial() != null
                || factura.getCodigoNumerico() != null
                || factura.getNumeroAutorizacion() != null
                || factura.getAmbiente() != null
                || factura.getEstablecimiento() != null
                || factura.getPuntoEmisionCodigo() != null
                || factura.getPuntoEmision() != null;
        boolean tieneDocumentos = !facturaDocumentoRepository
                .findAllByFactura_Id(factura.getId()).isEmpty();

        if (tieneNumeracion || tieneDocumentos) {
            throw new FacturaNoEditableException(factura.getId(), factura.getEstado());
        }
    }

    // ==================================================================
    // Apoyo
    // ==================================================================

    /** Carga la factura exigiendo que siga siendo editable. */
    private Factura borradorEditable(Long facturaId) {
        if (facturaId == null) {
            throw new IllegalArgumentException("El identificador de la factura es obligatorio.");
        }
        Factura factura = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la factura con id " + facturaId + "."));

        if (factura.getEstado() != EstadoFactura.BORRADOR) {
            throw new FacturaNoEditableException(facturaId, factura.getEstado());
        }
        return factura;
    }

    private void borrarDetalles(Factura factura) {
        if (factura.getDetalles().isEmpty()) {
            return;
        }
        facturaDetalleRepository.deleteAll(new ArrayList<>(factura.getDetalles()));
        factura.getDetalles().clear();
        // Se fuerzan los DELETE antes de insertar las lineas nuevas: sin esto,
        // Hibernate podria emitir los INSERT primero y chocar con el unico
        // (factura_id, linea).
        facturaDetalleRepository.flush();
    }

    private void borrarPagos(Factura factura) {
        if (factura.getPagos().isEmpty()) {
            return;
        }
        facturaPagoRepository.deleteAll(new ArrayList<>(factura.getPagos()));
        factura.getPagos().clear();
        facturaPagoRepository.flush();
    }

    private Usuario usuarioActivo(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el usuario con id " + usuarioId + "."));
        if (!usuario.isActivo()) {
            throw new TitularFacturaInvalidoException(
                    "El usuario " + usuarioId + " esta inactivo y no puede tener facturas nuevas.");
        }
        return usuario;
    }

    /**
     * La mascota es opcional, pero si se informa debe pertenecer al titular
     * FUNCIONAL de la factura. Ojo con la distincion: el comprador fiscal si
     * puede ser un tercero (la empresa del dueno, por ejemplo); la mascota no,
     * porque es el contexto clinico de quien recibe la atencion.
     */
    private Mascota mascotaDelUsuario(Long mascotaId, Long usuarioId) {
        if (mascotaId == null) {
            return null;
        }
        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la mascota con id " + mascotaId + "."));
        if (!mascota.isActivo()) {
            throw new TitularFacturaInvalidoException(
                    "La mascota " + mascotaId + " esta inactiva.");
        }
        if (!mascota.getDuenio().getId().equals(usuarioId)) {
            throw new TitularFacturaInvalidoException(
                    "La mascota " + mascotaId + " no pertenece al usuario " + usuarioId + ".");
        }
        return mascota;
    }

    private LocalDate exigirFecha(LocalDate fechaEmision) {
        if (fechaEmision == null) {
            throw new IllegalArgumentException("La fecha de emision es obligatoria.");
        }
        return fechaEmision;
    }
}
