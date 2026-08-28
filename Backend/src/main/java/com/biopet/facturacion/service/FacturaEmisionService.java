package com.biopet.facturacion.service;

import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.CalculoFacturaService;
import com.biopet.facturacion.domain.ClaveAccesoGenerator;
import com.biopet.facturacion.domain.ClaveAccesoRequest;
import com.biopet.facturacion.domain.CodigoNumericoGenerator;
import com.biopet.facturacion.domain.PagoFacturable;
import com.biopet.facturacion.domain.TipoComprobante;
import com.biopet.facturacion.domain.TipoEmisionSri;
import com.biopet.facturacion.domain.TotalesFactura;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaPago;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.exception.ConfiguracionFiscalInvalidaException;
import com.biopet.facturacion.exception.DatosFacturacionInvalidosException;
import com.biopet.facturacion.exception.PagosFacturaInvalidosException;
import com.biopet.facturacion.repository.FacturaRepository;
import com.biopet.facturacion.repository.PuntoEmisionRepository;
import com.biopet.facturacion.service.command.EmitirFacturaCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte un BORRADOR en una factura EMITIDA.
 *
 * <h2>Que significa EMITIDA</h2>
 *
 * <p>Que BIOPET congelo fiscalmente el comprobante: le asigno ambiente, serie,
 * secuencial, codigo numerico y clave de acceso, y fijo todos sus snapshots.
 * NO significa que el SRI la haya recibido ni autorizado; eso son estados
 * aparte que llegaran con el pipeline (XML, firma, recepcion, autorizacion),
 * completamente fuera de esta fase y fuera de esta transaccion.
 *
 * <h2>Orden de las operaciones</h2>
 *
 * <pre>
 *   bloquear factura (FOR UPDATE)
 *   -> si ya no es BORRADOR: devolverla tal cual (idempotencia)
 *   -> validar titular, comprador, punto de emision y emisor
 *   -> recalcular desde el catalogo y las tarifas vigentes
 *   -> validar que los pagos cuadren
 *   -> RESERVAR SECUENCIAL          <-- primer efecto irreversible
 *   -> generar codigo numerico
 *   -> componer clave de acceso
 *   -> congelar snapshots de emisor y numeracion
 *   -> estado = EMITIDA
 *   -> COMMIT
 * </pre>
 *
 * <p>El orden importa. Todo lo que puede fallar se comprueba ANTES de tocar el
 * contador, porque reservar bloquea la fila del secuencial y hace esperar a
 * cualquier otra emision de ese punto: no tiene sentido tomar ese bloqueo para
 * despues descubrir que faltaba una tarifa. Despues de reservar solo queda
 * trabajo local y rapido -generar dos cadenas y copiar campos-, sin una sola
 * llamada de red.
 */
@Service
public class FacturaEmisionService {

    /** Valor del tag {@code <moneda>} del comprobante. */
    private static final String MONEDA = "DOLAR";

    private final FacturaRepository facturaRepository;
    private final PuntoEmisionRepository puntoEmisionRepository;
    private final FacturaCalculador facturaCalculador;
    private final SecuencialService secuencialService;
    private final CalculoFacturaService calculoFacturaService;
    private final CodigoNumericoGenerator codigoNumericoGenerator;
    private final ClaveAccesoGenerator claveAccesoGenerator;

    public FacturaEmisionService(FacturaRepository facturaRepository,
                                 PuntoEmisionRepository puntoEmisionRepository,
                                 FacturaCalculador facturaCalculador,
                                 SecuencialService secuencialService,
                                 CalculoFacturaService calculoFacturaService,
                                 CodigoNumericoGenerator codigoNumericoGenerator,
                                 ClaveAccesoGenerator claveAccesoGenerator) {
        this.facturaRepository = facturaRepository;
        this.puntoEmisionRepository = puntoEmisionRepository;
        this.facturaCalculador = facturaCalculador;
        this.secuencialService = secuencialService;
        this.calculoFacturaService = calculoFacturaService;
        this.codigoNumericoGenerator = codigoNumericoGenerator;
        this.claveAccesoGenerator = claveAccesoGenerator;
    }

    /**
     * Emite localmente el borrador indicado.
     *
     * <p><b>Idempotente.</b> Si la factura ya no es BORRADOR se devuelve tal
     * cual, sin reservar otro secuencial, sin sortear otro codigo numerico y sin
     * recomponer la clave. Esto es lo que permitira que un futuro
     * {@code POST /facturas/{id}/emitir} se pueda reintentar sin miedo: un
     * usuario que pulsa dos veces, un reintento de red o dos pestanas abiertas
     * no pueden producir dos comprobantes ni quemar dos numeros.
     *
     * <p>{@link Transactional} con propagacion por defecto (REQUIRED). La
     * reserva del secuencial se une a ESTA transaccion, de modo que si algo
     * falla despues, el contador vuelve atras junto con la factura.
     */
    @Transactional
    public Factura emitir(EmitirFacturaCommand comando) {
        if (comando == null || comando.facturaId() == null) {
            throw new IllegalArgumentException("La factura a emitir es obligatoria.");
        }
        if (comando.puntoEmisionId() == null) {
            throw new IllegalArgumentException("El punto de emision es obligatorio para emitir.");
        }
        if (comando.ambiente() == null) {
            throw new IllegalArgumentException("El ambiente SRI es obligatorio para emitir.");
        }

        // 1. Bloqueo de la fila de la factura. Sin esto, dos peticiones
        //    simultaneas sobre el MISMO borrador pasarian las dos por la
        //    comprobacion de estado y consumirian DOS secuenciales: el lock del
        //    contador impide numeros repetidos, no emisiones repetidas.
        Factura factura = facturaRepository.bloquearParaEmitir(comando.facturaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la factura con id " + comando.facturaId() + "."));

        // 2. Idempotencia. El segundo hilo entra aqui cuando el primero ya
        //    confirmo, y ve la factura ya emitida.
        if (factura.getEstado() != EstadoFactura.BORRADOR) {
            return factura;
        }

        // 3. Validaciones que no dependen del contador. La fecha de emision no
        //    se comprueba aqui: es NOT NULL en la tabla, asi que una factura
        //    cargada de la base siempre la trae, y ClaveAccesoRequest la
        //    validaria de todos modos.
        validarComprador(factura);
        PuntoEmision puntoEmision = puntoEmisionValido(comando.puntoEmisionId());
        EmisorFiscal emisor = emisorValido(puntoEmision);

        // 4. Recalculo definitivo desde las fuentes vivas: precios del catalogo
        //    y tarifa vigente en la fecha de emision. Lo que guardo el borrador
        //    era provisional; esto es lo que se congela.
        TotalesFactura totales = facturaCalculador.recalcularYVolcar(factura);
        if (totales == null) {
            throw new IllegalArgumentException(
                    "La factura " + factura.getId() + " no tiene lineas y no puede emitirse.");
        }

        // 5. Los pagos deben cubrir el importe EXACTO. Se valida antes de
        //    numerar: un descuadre es del documento, no de la numeracion.
        validarPagos(factura, totales);

        // 6. Primer efecto irreversible del flujo.
        long secuencial = secuencialService.reservar(puntoEmision.getId(), comando.ambiente());

        // 7. A partir de aqui, solo trabajo local.
        String codigoNumerico = codigoNumericoGenerator.generar();
        String claveAcceso = claveAccesoGenerator.generar(new ClaveAccesoRequest(
                factura.getFechaEmision(),
                TipoComprobante.FACTURA,
                emisor.getRuc(),
                comando.ambiente(),
                puntoEmision.getEstablecimiento(),
                puntoEmision.getPuntoEmision(),
                secuencial,
                codigoNumerico,
                TipoEmisionSri.NORMAL));

        aplicarNumeracion(factura, puntoEmision, comando.ambiente(),
                secuencial, codigoNumerico, claveAcceso);
        congelarEmisor(factura, puntoEmision, emisor);

        factura.setMoneda(MONEDA);
        factura.setEstado(EstadoFactura.EMITIDA);

        return facturaRepository.saveAndFlush(factura);
    }

    // ==================================================================
    // Validaciones previas al secuencial
    // ==================================================================

    /**
     * Coherencia ESTRUCTURAL del comprador: tipo, identificacion y razon social.
     *
     * <p>No se comprueba la validez normativa de la identificacion (que un RUC
     * exista de verdad lo resuelve el SRI al autorizar, errores 46 y 63), ni se
     * sustituye nada por consumidor final: si el usuario no eligio ese tipo, no
     * se le asigna por su cuenta.
     *
     * <p>Se valida el SNAPSHOT del borrador, no {@code DatosFacturacion}. Es
     * deliberado: ver {@code FacturaBorradorService.seleccionarComprador}.
     */
    private void validarComprador(Factura factura) {
        if (factura.getCompradorTipoIdentificacion() == null) {
            throw new DatosFacturacionInvalidosException(
                    "La factura " + factura.getId() + " no tiene tipo de identificacion del comprador. "
                            + "Seleccione los datos de facturacion antes de emitir.");
        }
        if (esVacio(factura.getCompradorIdentificacion())) {
            throw new DatosFacturacionInvalidosException(
                    "La factura " + factura.getId() + " no tiene identificacion del comprador.");
        }
        if (esVacio(factura.getCompradorRazonSocial())) {
            throw new DatosFacturacionInvalidosException(
                    "La factura " + factura.getId() + " no tiene razon social del comprador.");
        }
    }

    private PuntoEmision puntoEmisionValido(Long puntoEmisionId) {
        PuntoEmision puntoEmision = puntoEmisionRepository.findById(puntoEmisionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el punto de emision con id " + puntoEmisionId + "."));
        if (!puntoEmision.isActivo()) {
            throw new ConfiguracionFiscalInvalidaException(
                    "El punto de emision " + puntoEmisionId + " esta inactivo y no puede emitir.");
        }
        return puntoEmision;
    }

    private EmisorFiscal emisorValido(PuntoEmision puntoEmision) {
        // La relacion es optional=false y la FK NOT NULL: el emisor siempre
        // esta. Lo unico que puede fallar es que lo hayan desactivado.
        EmisorFiscal emisor = puntoEmision.getEmisorFiscal();
        if (!emisor.isActivo()) {
            throw new ConfiguracionFiscalInvalidaException(
                    "El emisor fiscal " + emisor.getId() + " esta inactivo y no puede emitir.");
        }
        return emisor;
    }

    /**
     * Delega la comprobacion en {@link CalculoFacturaService}, que compara con
     * BigDecimal en escala fiscal y sin tolerancias, y traduce su
     * {@code IllegalArgumentException} a una excepcion de dominio del modulo.
     */
    private void validarPagos(Factura factura, TotalesFactura totales) {
        List<PagoFacturable> pagos = new ArrayList<>(factura.getPagos().size());
        for (FacturaPago pago : factura.getPagos()) {
            pagos.add(new PagoFacturable(pago.getFormaPago(), pago.getTotal()));
        }
        try {
            calculoFacturaService.validarPagos(totales, pagos);
        } catch (IllegalArgumentException e) {
            throw new PagosFacturaInvalidosException(
                    "La factura " + factura.getId() + " no puede emitirse: " + e.getMessage());
        }
    }

    // ==================================================================
    // Congelacion
    // ==================================================================

    private void aplicarNumeracion(Factura factura, PuntoEmision puntoEmision, AmbienteSri ambiente,
                                   long secuencial, String codigoNumerico, String claveAcceso) {
        factura.setPuntoEmision(puntoEmision);
        factura.setAmbiente(ambiente);
        factura.setEstablecimiento(puntoEmision.getEstablecimiento());
        factura.setPuntoEmisionCodigo(puntoEmision.getPuntoEmision());
        factura.setSecuencial(secuencial);
        factura.setCodigoNumerico(codigoNumerico);
        factura.setClaveAcceso(claveAcceso);
    }

    /**
     * Copia los datos tributarios del emisor al comprobante. Desde este momento,
     * que un administrador cambie el RUC, la razon social o la direccion del
     * emisor no altera esta factura: el XML que se genere manana debe decir lo
     * mismo que decia el dia que se emitio.
     */
    private void congelarEmisor(Factura factura, PuntoEmision puntoEmision, EmisorFiscal emisor) {
        factura.setEmisorRuc(emisor.getRuc());
        factura.setEmisorRazonSocial(emisor.getRazonSocial());
        factura.setEmisorNombreComercial(emisor.getNombreComercial());
        factura.setEmisorDireccionMatriz(emisor.getDireccionMatriz());
        factura.setEmisorDireccionEstablecimiento(puntoEmision.getDireccionEstablecimiento());
        factura.setEmisorObligadoContabilidad(emisor.isObligadoContabilidad());
        factura.setEmisorContribuyenteEspecial(emisor.getContribuyenteEspecial());
        factura.setEmisorRimpe(emisor.isRimpe());
        factura.setEmisorAgenteRetencionResolucion(emisor.getAgenteRetencionResolucion());
    }

    private boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
