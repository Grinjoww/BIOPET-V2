package com.biopet.facturacion.service;

import com.biopet.facturacion.domain.CalculoFacturaService;
import com.biopet.facturacion.domain.LineaCalculada;
import com.biopet.facturacion.domain.LineaFacturable;
import com.biopet.facturacion.domain.TotalesFactura;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.FacturaDetalle;
import com.biopet.facturacion.entity.TarifaImpuesto;
import com.biopet.facturacion.exception.ConceptoFacturableNoDisponibleException;
import com.biopet.facturacion.repository.ConceptoFacturableRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Recalcula una factura entera desde las fuentes vivas y vuelca el resultado en
 * sus entidades.
 *
 * <p>Es el corazon compartido por el borrador y la emision, y es intencional que
 * sea EL MISMO codigo en ambos casos:
 *
 * <ul>
 *   <li>en BORRADOR produce una vista provisional, util para que el usuario vea
 *       lo que va a pagar;</li>
 *   <li>en la EMISION produce los valores definitivos, que quedan congelados.</li>
 * </ul>
 *
 * <p>Por eso la emision vuelve a ejecutarlo en lugar de confiar en lo que el
 * borrador dejo guardado: entre guardar un borrador y emitirlo pueden pasar dias
 * y en ese tiempo puede haber cambiado el precio del concepto, la tarifa vigente
 * o incluso haberse dado de baja el concepto. El valor que vale es el del
 * momento de emitir, no el que se vio al armar el borrador.
 *
 * <p>De cada linea se conserva unicamente lo que pidio quien factura -cantidad,
 * descuento y trazabilidad-; el precio, la descripcion, el codigo de impuesto y
 * la tarifa se releen siempre del catalogo. El caller nunca los aporta.
 *
 * <p>La aritmetica no se implementa aqui: se delega integra en
 * {@link CalculoFacturaService} (Fase 2), que ya define la politica de redondeo,
 * la agrupacion de impuestos y los totales. Esta clase solo traduce entre
 * entidades JPA y los value objects de aquel.
 */
@Component
public class FacturaCalculador {

    private final ConceptoFacturableRepository conceptoFacturableRepository;
    private final TarifaImpuestoResolver tarifaImpuestoResolver;
    private final CalculoFacturaService calculoFacturaService;

    public FacturaCalculador(ConceptoFacturableRepository conceptoFacturableRepository,
                             TarifaImpuestoResolver tarifaImpuestoResolver,
                             CalculoFacturaService calculoFacturaService) {
        this.conceptoFacturableRepository = conceptoFacturableRepository;
        this.tarifaImpuestoResolver = tarifaImpuestoResolver;
        this.calculoFacturaService = calculoFacturaService;
    }

    /**
     * Recalcula todas las lineas de la factura y escribe el resultado tanto en
     * cada {@link FacturaDetalle} como en los totales de la cabecera.
     *
     * @return los totales calculados, o {@code null} si la factura no tiene
     *         lineas (borrador vacio: totales a cero, nada que calcular).
     * @throws ConceptoFacturableNoDisponibleException si algun concepto
     *         desaparecio o fue dado de baja.
     */
    public TotalesFactura recalcularYVolcar(Factura factura) {
        List<FacturaDetalle> detalles = factura.getDetalles().stream()
                .sorted(Comparator.comparing(FacturaDetalle::getLinea))
                .toList();

        if (detalles.isEmpty()) {
            volcarTotalesVacios(factura);
            return null;
        }

        List<LineaFacturable> lineas = new ArrayList<>(detalles.size());
        for (FacturaDetalle detalle : detalles) {
            ConceptoFacturable concepto = conceptoVigente(detalle);
            TarifaImpuesto tarifa = tarifaImpuestoResolver.resolver(
                    concepto.getCodigoImpuesto(),
                    concepto.getCodigoPorcentaje(),
                    factura.getFechaEmision());

            lineas.add(new LineaFacturable(
                    concepto.getCodigo(),
                    concepto.getDescripcion(),
                    detalle.getCantidad(),
                    concepto.getPrecioUnitario(),
                    descuentoDe(detalle),
                    concepto.getCodigoImpuesto(),
                    concepto.getCodigoPorcentaje(),
                    tarifa.getTarifa()));
        }

        TotalesFactura totales = calculoFacturaService.calcular(lineas);

        for (int i = 0; i < detalles.size(); i++) {
            volcarLinea(detalles.get(i), totales.lineas().get(i));
        }
        volcarTotales(factura, totales);

        return totales;
    }

    private ConceptoFacturable conceptoVigente(FacturaDetalle detalle) {
        Long conceptoId = detalle.getConceptoFacturable() == null
                ? null
                : detalle.getConceptoFacturable().getId();
        if (conceptoId == null) {
            // En esta fase toda linea nace de un concepto del catalogo; las
            // lineas de texto libre no existen todavia (para lo excepcional
            // esta el tipo OTRO).
            throw new ConceptoFacturableNoDisponibleException(null);
        }
        return conceptoFacturableRepository.findByIdAndActivoTrue(conceptoId)
                .orElseThrow(() -> new ConceptoFacturableNoDisponibleException(conceptoId));
    }

    /** Copia el resultado del calculo al snapshot de la linea. */
    private void volcarLinea(FacturaDetalle detalle, LineaCalculada calculada) {
        LineaFacturable origen = calculada.origen();

        detalle.setCodigoPrincipal(origen.codigoPrincipal());
        detalle.setDescripcion(origen.descripcion());
        detalle.setPrecioUnitario(origen.precioUnitario());
        detalle.setDescuento(origen.descuento());
        detalle.setImpuestoCodigo(origen.codigoImpuesto());
        detalle.setImpuestoCodigoPorcentaje(origen.codigoPorcentaje());
        detalle.setImpuestoTarifa(origen.tarifa());

        detalle.setPrecioTotalSinImpuesto(calculada.precioTotalSinImpuesto());
        detalle.setBaseImponible(calculada.baseImponible());
        detalle.setImpuestoValor(calculada.valorImpuesto());
    }

    private void volcarTotales(Factura factura, TotalesFactura totales) {
        factura.setTotalSinImpuestos(totales.totalSinImpuestos());
        factura.setTotalDescuento(totales.totalDescuento());
        factura.setTotalImpuestos(totales.totalImpuestos());
        factura.setImporteTotal(totales.importeTotal());
    }

    private void volcarTotalesVacios(Factura factura) {
        BigDecimal cero = BigDecimal.ZERO.setScale(2);
        factura.setTotalSinImpuestos(cero);
        factura.setTotalDescuento(cero);
        factura.setTotalImpuestos(cero);
        factura.setImporteTotal(cero);
    }

    private BigDecimal descuentoDe(FacturaDetalle detalle) {
        return detalle.getDescuento() == null ? BigDecimal.ZERO : detalle.getDescuento();
    }
}
