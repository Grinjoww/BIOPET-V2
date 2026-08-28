package com.biopet.facturacion.service;

import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.exception.FacturaXmlInvalidoException;
import com.biopet.facturacion.repository.FacturaDocumentoRepository;
import com.biopet.facturacion.repository.FacturaRepository;
import com.biopet.facturacion.xml.FacturaXmlBuilder;
import com.biopet.facturacion.xml.FacturaXsdValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Genera y guarda el XML de una factura emitida como
 * {@link TipoDocumentoFactura#XML_GENERADO}.
 *
 * <p>Alcance: construir, validar contra el XSD oficial y persistir. Aqui NO se
 * firma (XAdES es la fase siguiente), no se habla con el SRI y no se toca ni la
 * numeracion ni los snapshots de la factura. Este servicio solo lee la factura y
 * escribe una fila en {@code factura_documentos}.
 *
 * <p>El XML producido es el input EXACTO de la firma: conserva
 * {@code id="comprobante"} y {@code version="2.1.0"} y no lleva
 * {@code ds:Signature}.
 */
@Service
public class FacturaXmlService {

    private final FacturaRepository facturaRepository;
    private final FacturaDocumentoRepository facturaDocumentoRepository;
    private final FacturaXmlBuilder facturaXmlBuilder;
    private final FacturaXsdValidator facturaXsdValidator;

    public FacturaXmlService(FacturaRepository facturaRepository,
                             FacturaDocumentoRepository facturaDocumentoRepository,
                             FacturaXmlBuilder facturaXmlBuilder,
                             FacturaXsdValidator facturaXsdValidator) {
        this.facturaRepository = facturaRepository;
        this.facturaDocumentoRepository = facturaDocumentoRepository;
        this.facturaXmlBuilder = facturaXmlBuilder;
        this.facturaXsdValidator = facturaXsdValidator;
    }

    /**
     * Genera el XML de la factura, o devuelve el ya generado.
     *
     * <p><b>Idempotente.</b> Si ya existe un XML_GENERADO se comprueba que su
     * SHA-256 siga correspondiendo a los bytes guardados y se devuelve ese mismo
     * documento, sin reconstruir nada. Importa porque en fases posteriores este
     * XML sera el que se firme y se reenvie al SRI en cada reintento: regenerarlo
     * podria producir bytes distintos y, una vez firmado, invalidar la firma.
     *
     * <p>La verificacion del hash al releer no es decorativa: si los bytes de la
     * base no corresponden a su hash, algo los corrompio y es mejor detenerse que
     * enviar al SRI un comprobante alterado.
     *
     * <p>Toma un bloqueo pesimista sobre la fila de la factura para que dos
     * peticiones simultaneas no intenten insertar dos veces el mismo tipo de
     * documento y choquen contra {@code uq_factura_documentos_tipo}.
     */
    @Transactional
    public FacturaDocumento generarXml(Long facturaId) {
        if (facturaId == null) {
            throw new IllegalArgumentException("El identificador de la factura es obligatorio.");
        }

        Factura factura = facturaRepository.bloquearParaGenerarDocumentos(facturaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la factura con id " + facturaId + "."));

        Optional<FacturaDocumento> existente = facturaDocumentoRepository
                .findByFactura_IdAndTipo(facturaId, TipoDocumentoFactura.XML_GENERADO);
        if (existente.isPresent()) {
            return verificarIntegridad(existente.get());
        }

        // El builder exige que la factura este emitida y que sus snapshots
        // reconcilien; el validador, que el resultado cumpla el XSD oficial.
        byte[] xml = facturaXmlBuilder.construir(factura);
        facturaXsdValidator.validar(xml);

        FacturaDocumento documento = FacturaDocumento.builder()
                .factura(factura)
                .tipo(TipoDocumentoFactura.XML_GENERADO)
                .contenido(xml)
                .sha256(sha256(xml))
                .bytes(xml.length)
                .build();

        return facturaDocumentoRepository.saveAndFlush(documento);
    }

    /** SHA-256 en hexadecimal minuscula de 64 caracteres, como exige el CHECK de V8. */
    public static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, el entorno esta roto.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM.", e);
        }
    }

    private FacturaDocumento verificarIntegridad(FacturaDocumento documento) {
        String real = sha256(documento.getContenido());
        if (!real.equals(documento.getSha256())) {
            throw new FacturaXmlInvalidoException(
                    "El XML guardado de la factura " + documento.getFactura().getId()
                            + " no corresponde a su SHA-256 (esperado " + documento.getSha256()
                            + ", calculado " + real + "). El contenido esta corrupto.");
        }
        return documento;
    }
}
