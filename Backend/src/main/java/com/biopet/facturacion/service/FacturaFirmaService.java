package com.biopet.facturacion.service;

import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDocumento;
import com.biopet.facturacion.entity.TipoDocumentoFactura;
import com.biopet.facturacion.exception.FacturaXmlInvalidoException;
import com.biopet.facturacion.exception.FirmaElectronicaException;
import com.biopet.facturacion.firma.CertificadoFirmaProvider;
import com.biopet.facturacion.firma.FacturaXadesSigner;
import com.biopet.facturacion.firma.FirmaProperties;
import com.biopet.facturacion.firma.FirmaXadesVerificador;
import com.biopet.facturacion.firma.MaterialFirma;
import com.biopet.facturacion.repository.FacturaDocumentoRepository;
import com.biopet.facturacion.repository.FacturaRepository;
import com.biopet.facturacion.xml.FacturaXsdValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Firma el XML de una factura emitida y lo guarda como
 * {@link TipoDocumentoFactura#XML_FIRMADO}.
 *
 * <p>Firmar NO es emitir ni autorizar. La factura sigue {@code EMITIDA}: este
 * servicio no toca el secuencial, la clave de acceso, los snapshots ni el estado.
 * Solo anade una fila a {@code factura_documentos}. El dialogo con el SRI
 * (recepcion y autorizacion) es una fase posterior.
 *
 * <p><b>No se vuelve a firmar si ya hay un XML_FIRMADO integro.</b> Una firma
 * XAdES-BES incluye {@code SigningTime}, asi que firmar dos veces el mismo
 * comprobante produce bytes distintos aunque los datos fiscales sean identicos.
 * Lo persistido es la fuente de verdad: si se regenerase, el documento que se
 * envia al SRI podria dejar de coincidir con el que BIOPET guardo.
 */
@Service
public class FacturaFirmaService {

    private final FacturaRepository facturaRepository;
    private final FacturaDocumentoRepository facturaDocumentoRepository;
    private final CertificadoFirmaProvider certificadoFirmaProvider;
    private final FacturaXadesSigner facturaXadesSigner;
    private final FirmaXadesVerificador firmaXadesVerificador;
    private final FacturaXsdValidator facturaXsdValidator;
    private final FirmaProperties firmaProperties;

    public FacturaFirmaService(FacturaRepository facturaRepository,
                               FacturaDocumentoRepository facturaDocumentoRepository,
                               CertificadoFirmaProvider certificadoFirmaProvider,
                               FacturaXadesSigner facturaXadesSigner,
                               FirmaXadesVerificador firmaXadesVerificador,
                               FacturaXsdValidator facturaXsdValidator,
                               FirmaProperties firmaProperties) {
        this.facturaRepository = facturaRepository;
        this.facturaDocumentoRepository = facturaDocumentoRepository;
        this.certificadoFirmaProvider = certificadoFirmaProvider;
        this.facturaXadesSigner = facturaXadesSigner;
        this.firmaXadesVerificador = firmaXadesVerificador;
        this.facturaXsdValidator = facturaXsdValidator;
        this.firmaProperties = firmaProperties;
    }

    /**
     * Firma el XML generado de la factura, o devuelve el firmado que ya exista.
     *
     * <p>Flujo:
     * <pre>
     *   bloquear la factura (serializa dos peticiones simultaneas)
     *   -> si ya hay XML_FIRMADO: comprobar su SHA-256 y su firma, y devolverlo
     *   -> exigir XML_GENERADO y comprobar su SHA-256
     *   -> cargar el material PKCS#12
     *   -> firmar EXACTAMENTE esos bytes
     *   -> verificar la firma
     *   -> validar contra el XSD oficial
     *   -> guardar XML_FIRMADO
     * </pre>
     */
    @Transactional
    public FacturaDocumento firmarFactura(Long facturaId) {
        if (facturaId == null) {
            throw new IllegalArgumentException("El identificador de la factura es obligatorio.");
        }

        Factura factura = facturaRepository.bloquearParaGenerarDocumentos(facturaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la factura con id " + facturaId + "."));

        Optional<FacturaDocumento> yaFirmado = facturaDocumentoRepository
                .findByFactura_IdAndTipo(facturaId, TipoDocumentoFactura.XML_FIRMADO);
        if (yaFirmado.isPresent()) {
            return verificarExistente(yaFirmado.get(), facturaId);
        }

        FacturaDocumento generado = facturaDocumentoRepository
                .findByFactura_IdAndTipo(facturaId, TipoDocumentoFactura.XML_GENERADO)
                .orElseThrow(() -> new FacturaXmlInvalidoException(
                        "La factura " + facturaId + " no tiene XML generado todavia: "
                                + "no hay nada que firmar."));

        // Nunca firmar bytes corruptos: la firma los legitimaria.
        exigirIntegridad(generado, facturaId, "generado");

        MaterialFirma material = certificadoFirmaProvider.material();
        byte[] firmado = facturaXadesSigner.firmar(
                generado.getContenido(), material, firmaProperties.getAlgoritmo());

        // Si la firma no verifica, no se guarda: un comprobante con una firma
        // invalida es peor que uno sin firmar, porque parece valido.
        firmaXadesVerificador.exigirValida(firmado, facturaId);
        // Y el comprobante firmado debe seguir cumpliendo el XSD oficial: la
        // firma va dentro del propio <factura>, asi que puede romperlo.
        facturaXsdValidator.validar(firmado);

        FacturaDocumento documento = FacturaDocumento.builder()
                .factura(factura)
                .tipo(TipoDocumentoFactura.XML_FIRMADO)
                .contenido(firmado)
                .sha256(FacturaXmlService.sha256(firmado))
                .bytes(firmado.length)
                .build();

        return facturaDocumentoRepository.saveAndFlush(documento);
    }

    /**
     * Un XML_FIRMADO ya guardado se devuelve tal cual, pero antes se comprueba
     * que no se haya corrompido y que su firma siga verificando.
     */
    private FacturaDocumento verificarExistente(FacturaDocumento documento, Long facturaId) {
        exigirIntegridad(documento, facturaId, "firmado");
        if (!firmaXadesVerificador.esValida(documento.getContenido())) {
            throw new FirmaElectronicaException(
                    "El XML firmado guardado de la factura " + facturaId
                            + " ya no verifica. No se devuelve un comprobante con firma invalida.");
        }
        return documento;
    }

    private void exigirIntegridad(FacturaDocumento documento, Long facturaId, String etiqueta) {
        String real = FacturaXmlService.sha256(documento.getContenido());
        if (!real.equals(documento.getSha256())) {
            throw new FacturaXmlInvalidoException(
                    "El XML " + etiqueta + " de la factura " + facturaId
                            + " no corresponde a su SHA-256 (esperado " + documento.getSha256()
                            + ", calculado " + real + "). El contenido esta corrupto.");
        }
    }
}
