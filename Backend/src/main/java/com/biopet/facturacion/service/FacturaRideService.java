package com.biopet.facturacion.service;

import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.exception.RideNoDisponibleException;
import com.biopet.facturacion.repository.FacturaDocumentoRepository;
import com.biopet.facturacion.repository.FacturaRepository;
import com.biopet.facturacion.ride.FacturaRideBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Genera y guarda el RIDE de una factura AUTORIZADA como
 * {@link TipoDocumentoFactura#RIDE_PDF}.
 *
 * <p>Mismo patron exacto que {@link FacturaXmlService#generarXml}: bloqueo
 * pesimista de la fila de la factura, comprobacion de existencia antes de
 * construir nada, verificacion de integridad por SHA-256 al releer. La unica
 * diferencia real es la condicion de negocio -aqui exige AUTORIZADA con datos
 * de autorizacion completos, alli exigia (implicitamente, dentro del builder)
 * que hubiese numeracion- y que el binario es un PDF, no un XML.
 *
 * <h2>Idempotencia</h2>
 *
 * <p><b>Primera llamada:</b> no existe {@code RIDE_PDF} todavia -&gt; se
 * construye con {@link FacturaRideBuilder} y se persiste. <b>Llamadas
 * siguientes:</b> ya existe -&gt; se devuelven los MISMOS bytes ya guardados,
 * sin reconstruir nada. Nunca se regenera un RIDE "distinto" de una factura ya
 * autorizada: el documento impreso de un comprobante fiscal no puede cambiar
 * cada vez que alguien lo descarga.
 */
@Service
public class FacturaRideService {

    private final FacturaRepository facturaRepository;
    private final FacturaDocumentoRepository facturaDocumentoRepository;
    private final FacturaRideBuilder facturaRideBuilder;

    public FacturaRideService(FacturaRepository facturaRepository,
                              FacturaDocumentoRepository facturaDocumentoRepository,
                              FacturaRideBuilder facturaRideBuilder) {
        this.facturaRepository = facturaRepository;
        this.facturaDocumentoRepository = facturaDocumentoRepository;
        this.facturaRideBuilder = facturaRideBuilder;
    }

    /**
     * @throws RecursoNoEncontradoException si la factura no existe (404).
     * @throws RideNoDisponibleException    si la factura todavia no esta en
     *         condiciones de tener RIDE (409): no esta AUTORIZADA, o le faltan
     *         datos de autorizacion.
     */
    @Transactional
    public FacturaDocumento generarRide(Long facturaId) {
        if (facturaId == null) {
            throw new IllegalArgumentException("El identificador de la factura es obligatorio.");
        }

        // Mismo lock que generarXml/firmarFactura: serializa dos peticiones
        // simultaneas del RIDE de la MISMA factura contra el indice unico
        // (factura_id, tipo) de factura_documentos.
        Factura factura = facturaRepository.bloquearParaGenerarDocumentos(facturaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la factura con id " + facturaId + "."));

        Optional<FacturaDocumento> existente = facturaDocumentoRepository
                .findByFactura_IdAndTipo(facturaId, TipoDocumentoFactura.RIDE_PDF);
        if (existente.isPresent()) {
            return verificarIntegridad(existente.get());
        }

        exigirAutorizadaConDatosCompletos(factura);

        byte[] pdf = facturaRideBuilder.construir(factura);

        FacturaDocumento documento = FacturaDocumento.builder()
                .factura(factura)
                .tipo(TipoDocumentoFactura.RIDE_PDF)
                .contenido(pdf)
                .sha256(FacturaXmlService.sha256(pdf))
                .bytes(pdf.length)
                .build();

        return facturaDocumentoRepository.saveAndFlush(documento);
    }

    /**
     * El RIDE es la representacion impresa de un comprobante YA AUTORIZADO por
     * el SRI: no existe "RIDE de borrador", "RIDE de emitida" ni "RIDE de
     * rechazada". La comprobacion de los tres campos de autorizacion (no solo
     * del estado) protege ademas contra un estado inconsistente que la propia
     * maquina de estados no deberia permitir, pero que este servicio no da por
     * sentado.
     */
    private void exigirAutorizadaConDatosCompletos(Factura factura) {
        if (factura.getEstado() != EstadoFactura.AUTORIZADA) {
            throw new RideNoDisponibleException(
                    "La factura " + factura.getId() + " esta " + factura.getEstado()
                            + " y todavia no tiene RIDE disponible: el RIDE solo existe para facturas "
                            + "autorizadas por el SRI.");
        }
        if (factura.getNumeroAutorizacion() == null || factura.getFechaAutorizacion() == null
                || factura.getClaveAcceso() == null) {
            throw new RideNoDisponibleException(
                    "La factura " + factura.getId()
                            + " esta AUTORIZADA pero le faltan datos de autorizacion; no se puede generar el RIDE.");
        }
    }

    private FacturaDocumento verificarIntegridad(FacturaDocumento documento) {
        String real = FacturaXmlService.sha256(documento.getContenido());
        if (!real.equals(documento.getSha256())) {
            throw new IllegalStateException(
                    "El RIDE guardado de la factura " + documento.getFactura().getId()
                            + " no corresponde a su SHA-256 (esperado " + documento.getSha256()
                            + ", calculado " + real + "). El contenido esta corrupto.");
        }
        return documento;
    }
}
