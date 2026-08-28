package com.biopet.facturacion.service;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.entity.TarifaImpuesto;
import com.biopet.facturacion.exception.TarifaImpuestoAmbiguaException;
import com.biopet.facturacion.exception.TarifaImpuestoNoConfiguradaException;
import com.biopet.facturacion.repository.TarifaImpuestoRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Resuelve que porcentaje tributario se aplica a un par (codigo de impuesto,
 * codigo de porcentaje) EN UNA FECHA concreta.
 *
 * <p>Vive aparte de los servicios de factura por dos razones: lo necesitan los
 * dos (el borrador para los totales provisionales, la emision para los
 * definitivos), y su regla -"debe haber exactamente una tarifa vigente"- es una
 * decision de configuracion tributaria que merece sus propias pruebas.
 *
 * <p>Nada aqui conoce el 15%, ni el IVA, ni que una consulta veterinaria lleve
 * un codigo concreto. Todo sale de la tabla {@code tarifa_impuesto}, que V7 deja
 * deliberadamente vacia para que nadie herede un porcentaje inventado.
 */
@Component
public class TarifaImpuestoResolver {

    private final TarifaImpuestoRepository tarifaImpuestoRepository;

    public TarifaImpuestoResolver(TarifaImpuestoRepository tarifaImpuestoRepository) {
        this.tarifaImpuestoRepository = tarifaImpuestoRepository;
    }

    /**
     * @return la unica tarifa activa cuyo periodo cubre {@code fecha}.
     * @throws TarifaImpuestoNoConfiguradaException si no hay ninguna.
     * @throws TarifaImpuestoAmbiguaException       si hay mas de una.
     */
    public TarifaImpuesto resolver(CodigoImpuestoSri codigoImpuesto, String codigoPorcentaje, LocalDate fecha) {
        if (codigoImpuesto == null || codigoPorcentaje == null || fecha == null) {
            throw new IllegalArgumentException(
                    "El codigo de impuesto, el codigo de porcentaje y la fecha son obligatorios "
                            + "para resolver una tarifa.");
        }

        List<TarifaImpuesto> aplicables =
                tarifaImpuestoRepository.findAplicables(codigoImpuesto, codigoPorcentaje, fecha);

        if (aplicables.isEmpty()) {
            throw new TarifaImpuestoNoConfiguradaException(codigoImpuesto, codigoPorcentaje, fecha);
        }
        if (aplicables.size() > 1) {
            // Dos periodos solapados son un error de configuracion. Quedarse
            // con el primero dejaria el importe de un comprobante fiscal a
            // merced del orden de las filas.
            throw new TarifaImpuestoAmbiguaException(
                    codigoImpuesto, codigoPorcentaje, fecha, aplicables.size());
        }
        return aplicables.get(0);
    }
}
