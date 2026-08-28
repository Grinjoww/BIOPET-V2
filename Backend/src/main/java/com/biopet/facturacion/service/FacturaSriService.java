package com.biopet.facturacion.service;

import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.EstadoRecepcionSri;
import com.biopet.facturacion.entity.OperacionSri;
import com.biopet.facturacion.exception.FacturaNoEnviableException;
import com.biopet.facturacion.firma.FirmaXadesVerificador;
import com.biopet.facturacion.sri.CodigosMensajeSri;
import com.biopet.facturacion.sri.ComprobanteParaEnvio;
import com.biopet.facturacion.sri.RespuestaAutorizacionSri;
import com.biopet.facturacion.sri.RespuestaRecepcionSri;
import com.biopet.facturacion.sri.ResultadoSriFactura;
import com.biopet.facturacion.sri.SriAutorizacionClient;
import com.biopet.facturacion.sri.SriComunicacionException;
import com.biopet.facturacion.sri.SriRecepcionClient;
import com.biopet.facturacion.xml.FacturaXsdValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orquesta el dialogo completo con el SRI:
 * recepcion, autorizacion y persistencia del desenlace.
 *
 * <pre>
 *   XML_FIRMADO
 *   -&gt; validarComprobante(byte[])
 *   -&gt; RECIBIDA / DEVUELTA
 *   -&gt; autorizacionComprobante(claveAcceso)
 *   -&gt; AUT / NAT / PPR
 * </pre>
 *
 * <h2>Esta clase NO es transaccional. A proposito.</h2>
 *
 * <p>No lleva {@code @Transactional} en ningun metodo, y es la propiedad mas
 * importante del diseno de esta fase. Toda la persistencia se delega en
 * {@link FacturaSriEstadoService}, cuyos metodos abren su propia transaccion
 * corta y la cierran antes de volver. Asi, entre la lectura de los datos y la
 * escritura del resultado no hay ninguna transaccion viva mientras se espera al
 * SRI, que puede tardar decenas de segundos.
 *
 * <h2>La regla que gobierna todos los reintentos</h2>
 *
 * <p><b>Una vez existen clave de acceso y XML firmado, jamas se crea otro
 * comprobante para reintentar.</b> Ni clave nueva, ni secuencial nuevo, ni
 * firma nueva. Un reintento reenvia exactamente los mismos bytes o, mejor aun,
 * pregunta antes por la misma clave. La razon es que el peor fallo posible de
 * un sistema de facturacion electronica no es no enviar: es enviar dos veces el
 * mismo hecho economico y que el SRI autorice ambos.
 *
 * <p>De ahi la asimetria deliberada de {@link #enviar(Long)}: si la factura ya
 * se intento enviar alguna vez, lo primero que hace es CONSULTAR autorizacion,
 * no reenviar. Preguntar es gratis y no tiene efectos; reenviar a ciegas
 * despues de un timeout es justamente el camino que produce duplicados.
 *
 * <h2>Lo que nunca se toca</h2>
 *
 * <p>Este servicio solo escribe los campos de estado frente al SRI
 * ({@code estado}, {@code estado_recepcion}, {@code estado_autorizacion},
 * {@code numero_autorizacion}, {@code fecha_autorizacion}, contadores) y anade
 * documentos y eventos. Nada fiscal: ni totales, ni lineas, ni snapshots, ni
 * numeracion.
 */
@Service
public class FacturaSriService {

    private static final Logger log = LoggerFactory.getLogger(FacturaSriService.class);

    private final FacturaSriEstadoService estadoService;
    private final SriRecepcionClient recepcionClient;
    private final SriAutorizacionClient autorizacionClient;
    private final FirmaXadesVerificador firmaXadesVerificador;
    private final FacturaXsdValidator facturaXsdValidator;

    public FacturaSriService(FacturaSriEstadoService estadoService,
                             SriRecepcionClient recepcionClient,
                             SriAutorizacionClient autorizacionClient,
                             FirmaXadesVerificador firmaXadesVerificador,
                             FacturaXsdValidator facturaXsdValidator) {
        this.estadoService = estadoService;
        this.recepcionClient = recepcionClient;
        this.autorizacionClient = autorizacionClient;
        this.firmaXadesVerificador = firmaXadesVerificador;
        this.facturaXsdValidator = facturaXsdValidator;
    }

    /**
     * Envia el comprobante al SRI y, si lo recibe, consulta su autorizacion.
     *
     * <pre>
     *   [TX corta] leer factura + XML firmado + comprobar SHA-256
     *   [sin TX]   verificar la firma XAdES y el XSD; si falla, NO se llama al SRI
     *   [sin TX]   si ya hubo un intento previo: consultar autorizacion primero
     *   [sin TX]   SOAP recepcion
     *   [TX corta] persistir recepcion
     *   [sin TX]   SOAP autorizacion
     *   [TX corta] persistir autorizacion (+ XML_AUTORIZADO)
     * </pre>
     *
     * @throws FacturaNoEnviableException si la factura no esta en condiciones.
     * @throws SriComunicacionException si el SRI no dio una respuesta funcional.
     *         La factura conserva intactos clave, secuencial y XML firmado, y el
     *         intento queda en la bitacora para poder reintentar.
     */
    public ResultadoSriFactura enviar(Long facturaId) {
        ComprobanteParaEnvio comprobante = estadoService.prepararEnvio(facturaId);

        // Idempotencia dura: una factura autorizada no se vuelve a enviar. Ni
        // una llamada al SRI, ni un cambio de estado.
        if (comprobante.estado() == EstadoFactura.AUTORIZADA) {
            log.info("Factura {}: ya AUTORIZADA. No se envia nada al SRI.", facturaId);
            return estadoService.estadoActual(facturaId);
        }
        if (comprobante.estado() != EstadoFactura.EMITIDA) {
            throw new FacturaNoEnviableException(
                    "La factura " + facturaId + " esta en estado " + comprobante.estado()
                            + " y no se puede enviar al SRI.");
        }

        verificarIntegridadLocal(comprobante);

        if (comprobante.huboIntentoPrevio()) {
            ResultadoSriFactura yaResuelta = consultarAntesDeReenviar(comprobante);
            if (yaResuelta != null) {
                return yaResuelta;
            }
        }

        RespuestaRecepcionSri recepcion = recepcion(comprobante);

        if (recepcion.recibida()) {
            estadoService.registrarRecepcion(facturaId, recepcion, false);
            return autorizar(comprobante);
        }

        // DEVUELTA. Hay dos devoluciones que NO son un rechazo del comprobante y
        // que no deben marcar la factura como RECHAZADA (ver los codigos).
        boolean rechazoDefinitivo = !esDevolucionNoDefinitiva(recepcion);
        ResultadoSriFactura resultado =
                estadoService.registrarRecepcion(facturaId, recepcion, rechazoDefinitivo);

        if (rechazoDefinitivo) {
            log.info("Factura {}: DEVUELTA por el SRI. Queda RECHAZADA conservando clave, "
                    + "secuencial y XML firmado.", facturaId);
            return resultado;
        }

        log.info("Factura {}: DEVUELTA con un codigo que indica que el SRI ya la tiene. No se "
                + "reenvia ni se genera clave nueva: se consulta su autorizacion.", facturaId);
        return autorizar(comprobante);
    }

    /**
     * Consulta la autorizacion de una factura ya enviada y actualiza su estado.
     *
     * <p>Es la operacion manual/interna que resuelve los pendientes: PPR,
     * timeouts y cualquier envio cuyo desenlace BIOPET no llego a ver. Se
     * expone como operacion propia -y no solo como paso interno de
     * {@code enviar}- porque en un despliegue gratuito de Render un scheduler no
     * es fiable: el servicio se duerme y las tareas periodicas no corren. Una
     * operacion explicita e idempotente funciona igual la despierte quien la
     * despierte.
     *
     * <p>Nunca reenvia el comprobante: solo pregunta. Preguntar no tiene efectos
     * y no puede duplicar nada.
     */
    public ResultadoSriFactura sincronizar(Long facturaId) {
        ComprobanteParaEnvio comprobante = estadoService.prepararSincronizacion(facturaId);

        if (comprobante.estado() == EstadoFactura.AUTORIZADA) {
            log.info("Factura {}: ya AUTORIZADA. No se consulta nada al SRI.", facturaId);
            return estadoService.estadoActual(facturaId);
        }

        return autorizar(comprobante);
    }

    // ==================================================================
    // Pasos
    // ==================================================================

    /**
     * Comprobaciones locales antes de gastar una llamada al SRI: la firma XAdES
     * debe verificar y el comprobante debe cumplir el XSD oficial.
     *
     * <p>El SHA-256 ya se comprobo al leer el documento, dentro de la
     * transaccion corta. Lo que queda -criptografia y validacion de esquema- se
     * hace aqui fuera, porque es CPU y no tiene por que ocupar una conexion de
     * base de datos.
     *
     * <p>Si algo de esto falla, no se abre la conexion. Enviar un comprobante
     * que ya se sabe invalido solo consigue una devolucion del SRI, ruido en la
     * bitacora y un diagnostico mas confuso que el mensaje local.
     */
    private void verificarIntegridadLocal(ComprobanteParaEnvio comprobante) {
        firmaXadesVerificador.exigirValida(comprobante.xmlFirmado(), comprobante.facturaId());
        facturaXsdValidator.validar(comprobante.xmlFirmado());
    }

    /**
     * Antes de reenviar algo que quizas ya llego, pregunta.
     *
     * @return el resultado si la consulta lo resolvio (AUT, NAT, o un PPR sobre
     *         una factura que el SRI consta haber recibido); {@code null} si hay
     *         que enviar de verdad.
     */
    private ResultadoSriFactura consultarAntesDeReenviar(ComprobanteParaEnvio comprobante) {
        log.info("Factura {}: ya hubo un intento previo. Se consulta autorizacion con la MISMA "
                + "clave antes de plantearse reenviar.", comprobante.facturaId());

        RespuestaAutorizacionSri respuesta = autorizacion(comprobante);
        ResultadoSriFactura resultado =
                estadoService.registrarAutorizacion(comprobante.facturaId(), respuesta);

        if (!respuesta.pendiente()) {
            return resultado;
        }
        // PPR. Si el SRI ya lo tiene (nos consta una recepcion), reenviar solo
        // generaria un codigo 43 y mas carga: se deja pendiente.
        if (comprobante.estadoRecepcion() == EstadoRecepcionSri.RECIBIDA) {
            log.info("Factura {}: el SRI la tiene en proceso y ya consta RECIBIDA. No se reenvia.",
                    comprobante.facturaId());
            return resultado;
        }
        // No consta que llegara (tipicamente, un timeout en recepcion): se envia.
        return null;
    }

    /** Llama a recepcion y, si falla tecnicamente, deja el fallo en la bitacora. */
    private RespuestaRecepcionSri recepcion(ComprobanteParaEnvio comprobante) {
        try {
            return recepcionClient.validarComprobante(comprobante.xmlFirmado());
        } catch (SriComunicacionException e) {
            estadoService.registrarFallo(comprobante.facturaId(), OperacionSri.RECEPCION, e);
            throw e;
        }
    }

    /** Llama a autorizacion y, si falla tecnicamente, deja el fallo en la bitacora. */
    private RespuestaAutorizacionSri autorizacion(ComprobanteParaEnvio comprobante) {
        try {
            return autorizacionClient.autorizacionComprobante(comprobante.claveAcceso());
        } catch (SriComunicacionException e) {
            estadoService.registrarFallo(comprobante.facturaId(), OperacionSri.AUTORIZACION, e);
            throw e;
        }
    }

    private ResultadoSriFactura autorizar(ComprobanteParaEnvio comprobante) {
        RespuestaAutorizacionSri respuesta = autorizacion(comprobante);
        return estadoService.registrarAutorizacion(comprobante.facturaId(), respuesta);
    }

    /**
     * Devoluciones que significan "el SRI ya tiene este comprobante", no "este
     * comprobante esta mal".
     *
     * <p>Se decide por CODIGO ({@code identificador}), no por el texto del
     * mensaje: el codigo es el contrato y el texto es descriptivo. En ambos
     * casos la accion correcta es la misma y es la contraria a la intuitiva: no
     * reenviar, no generar clave nueva, consultar autorizacion.
     */
    private static boolean esDevolucionNoDefinitiva(RespuestaRecepcionSri recepcion) {
        return recepcion.contieneCodigo(CodigosMensajeSri.CLAVE_REGISTRADA)
                || recepcion.contieneCodigo(CodigosMensajeSri.EN_PROCESAMIENTO);
    }
}
