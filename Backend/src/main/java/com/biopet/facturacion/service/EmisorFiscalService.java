package com.biopet.facturacion.service;

import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.dto.EmisorFiscalRequest;
import com.biopet.facturacion.dto.EmisorFiscalResponse;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.repository.EmisorFiscalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuracion del emisor fiscal para la Fase 8B.
 *
 * <p>BIOPET modela una sola clinica: {@code GET}/{@code PUT
 * /api/facturacion/emisor} operan sobre UNA fila unica de
 * {@code emisor_fiscal}, nunca una coleccion. {@link #actualizar} hace
 * "upsert": crea la fila si todavia no existe (primer despliegue) o actualiza
 * la existente; jamas inserta una segunda mientras la primera siga ahi, aunque
 * este {@code activo = false} -esa es, de hecho, la unica via REST para
 * reactivarla, ya que "No permitir borrar fisicamente" excluye un DELETE-.
 *
 * <p>{@link #obtener} devuelve la fila exista o no {@code activo}, para que un
 * ADMIN pueda verla y reactivarla sin quedar bloqueado tras desactivarla por
 * error.
 *
 * <p>Cambiar estos datos NO altera ninguna factura ya emitida:
 * {@code FacturaEmisionService#congelarEmisor} copia su propio snapshot al
 * emitir y nunca vuelve a leer esta tabla despues.
 */
@Service
public class EmisorFiscalService {

    private final EmisorFiscalRepository emisorFiscalRepository;

    public EmisorFiscalService(EmisorFiscalRepository emisorFiscalRepository) {
        this.emisorFiscalRepository = emisorFiscalRepository;
    }

    @Transactional(readOnly = true)
    public EmisorFiscalResponse obtener() {
        return toResponse(emisorFiscalRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Todavia no se ha configurado el emisor fiscal.")));
    }

    @Transactional
    public EmisorFiscalResponse actualizar(EmisorFiscalRequest request) {
        EmisorFiscal emisor = emisorFiscalRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> EmisorFiscal.builder().build());

        emisor.setRuc(request.ruc());
        emisor.setRazonSocial(request.razonSocial().trim());
        emisor.setNombreComercial(blankToNull(request.nombreComercial()));
        emisor.setDireccionMatriz(request.direccionMatriz().trim());
        emisor.setObligadoContabilidad(request.obligadoContabilidad());
        emisor.setContribuyenteEspecial(blankToNull(request.contribuyenteEspecial()));
        emisor.setRimpe(request.rimpe());
        emisor.setAgenteRetencionResolucion(blankToNull(request.agenteRetencionResolucion()));
        emisor.setActivo(request.activo());

        return toResponse(emisorFiscalRepository.save(emisor));
    }

    private static String blankToNull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private static EmisorFiscalResponse toResponse(EmisorFiscal e) {
        return new EmisorFiscalResponse(
                e.getId(), e.getRuc(), e.getRazonSocial(), e.getNombreComercial(), e.getDireccionMatriz(),
                e.isObligadoContabilidad(), e.getContribuyenteEspecial(), e.isRimpe(),
                e.getAgenteRetencionResolucion(), e.isActivo());
    }
}
