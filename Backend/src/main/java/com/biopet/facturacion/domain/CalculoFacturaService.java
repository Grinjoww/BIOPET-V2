package com.biopet.facturacion.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculo monetario de una factura. Clase pura: sin Spring, sin JPA, sin red.
 * Se instancia con {@code new} y para una misma entrada devuelve siempre la
 * misma salida.
 *
 * <h2>Politica de redondeo</h2>
 *
 * <p><b>Impuesto por el SRI.</b> Ficha v2.34, seccion 9.17: los importes viajan
 * con un maximo de 2 decimales; cantidad y precio unitario admiten hasta 6. El
 * XSD lo fija con facets ({@code fractionDigits}). El error 52 "Error en
 * diferencias - Cuando existe error en los calculos del comprobante" se valida
 * tanto en el emisor como en la autorizacion.
 *
 * <p><b>Decision nuestra.</b> Ni la ficha ni el XSD publican el modo de
 * redondeo ni el orden entre redondear y sumar. Se adopta:
 *
 * <ol>
 *   <li>{@code cantidad x precioUnitario} se calcula <b>exacto</b>, sin
 *       redondear (BigDecimal multiplica sin perdida).</li>
 *   <li>{@code precioTotalSinImpuesto = (bruto - descuento)} se redondea a 2
 *       decimales con HALF_UP. Es el <b>unico</b> redondeo de la linea en el
 *       tramo sin impuestos.</li>
 *   <li>{@code baseImponible = precioTotalSinImpuesto} (ya redondeado).</li>
 *   <li>{@code valorImpuesto = baseImponible x tarifa / 100}, redondeado una
 *       sola vez a 2 decimales con HALF_UP en la propia division.</li>
 *   <li>Los grupos de {@code totalConImpuestos} y los totales de la factura
 *       suman valores <b>que ya estan en escala 2</b>. Sumar decimales de igual
 *       escala es exacto, asi que no hay ningun segundo redondeo.</li>
 * </ol>
 *
 * <p>Consecuencia buscada: como no existe ningun "sumar y luego redondear", una
 * fraccion de centavo no puede aparecer en dos sitios a la vez, y por
 * construccion se cumple que la suma de las bases de los grupos es identica a
 * la suma de las bases de las lineas. Esa es la defensa frente al error 52.
 *
 * <p>Validacion empirica de la regla 2: el unico ejemplo numerico real de la
 * ficha usa {@code cantidad = 2.542563} y {@code precioUnitario = 25.542365};
 * el producto exacto es {@code 64.943072181495} y la ficha publica
 * {@code <precioTotalSinImpuesto>64.94</precioTotalSinImpuesto>}.
 */
public class CalculoFacturaService {

    public TotalesFactura calcular(List<LineaFacturable> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("La factura debe tener al menos una linea.");
        }

        List<LineaCalculada> calculadas = new ArrayList<>(lineas.size());
        for (LineaFacturable linea : lineas) {
            calculadas.add(calcularLinea(linea));
        }

        List<ImpuestoAgrupado> resumen = agruparImpuestos(calculadas);

        BigDecimal totalSinImpuestos = cero();
        BigDecimal totalDescuento = cero();
        for (LineaCalculada calculada : calculadas) {
            totalSinImpuestos = totalSinImpuestos.add(calculada.precioTotalSinImpuesto());
            totalDescuento = totalDescuento.add(calculada.origen().descuento());
        }

        BigDecimal totalImpuestos = cero();
        for (ImpuestoAgrupado grupo : resumen) {
            totalImpuestos = totalImpuestos.add(grupo.valorImpuesto());
        }

        // Las cuatro magnitudes ya estan en escala 2; la suma es exacta.
        BigDecimal importeTotal = totalSinImpuestos.add(totalImpuestos);

        return new TotalesFactura(
                calculadas,
                resumen,
                totalSinImpuestos,
                EscalasSri.aMonetario(totalDescuento),
                totalImpuestos,
                importeTotal);
    }

    public LineaCalculada calcularLinea(LineaFacturable linea) {
        if (linea == null) {
            throw new IllegalArgumentException("La linea no puede ser nula.");
        }

        BigDecimal bruto = linea.importeBruto();
        BigDecimal precioTotalSinImpuesto = EscalasSri.aMonetario(bruto.subtract(linea.descuento()));
        BigDecimal baseImponible = precioTotalSinImpuesto;
        BigDecimal valorImpuesto = baseImponible
                .multiply(linea.tarifa())
                .divide(EscalasSri.CIEN, EscalasSri.ESCALA_MONETARIA, EscalasSri.REDONDEO);

        return new LineaCalculada(
                linea,
                precioTotalSinImpuesto,
                baseImponible,
                valorImpuesto,
                precioTotalSinImpuesto.add(valorImpuesto));
    }

    /**
     * Agrupa por (codigo de impuesto, codigo de porcentaje) conservando el orden
     * de primera aparicion, para que el XML sea estable y diffeable.
     */
    public List<ImpuestoAgrupado> agruparImpuestos(List<LineaCalculada> lineas) {
        Map<String, ImpuestoAgrupado> acumulado = new LinkedHashMap<>();

        for (LineaCalculada calculada : lineas) {
            LineaFacturable origen = calculada.origen();
            String clave = origen.claveImpuesto();
            ImpuestoAgrupado previo = acumulado.get(clave);

            if (previo == null) {
                acumulado.put(clave, new ImpuestoAgrupado(
                        origen.codigoImpuesto(),
                        origen.codigoPorcentaje(),
                        origen.tarifa(),
                        calculada.baseImponible(),
                        calculada.valorImpuesto()));
                continue;
            }

            if (previo.tarifa().compareTo(origen.tarifa()) != 0) {
                throw new IllegalArgumentException(
                        "Dos lineas comparten el codigo de porcentaje \"" + origen.codigoPorcentaje()
                                + "\" pero declaran tarifas distintas (" + previo.tarifa() + " y "
                                + origen.tarifa() + ").");
            }

            acumulado.put(clave, new ImpuestoAgrupado(
                    previo.codigoImpuesto(),
                    previo.codigoPorcentaje(),
                    previo.tarifa(),
                    previo.baseImponible().add(calculada.baseImponible()),
                    previo.valorImpuesto().add(calculada.valorImpuesto())));
        }

        return List.copyOf(acumulado.values());
    }

    /** Suma de las formas de pago, en escala monetaria. */
    public BigDecimal sumarPagos(List<PagoFacturable> pagos) {
        if (pagos == null) {
            throw new IllegalArgumentException("La lista de pagos no puede ser nula.");
        }
        BigDecimal suma = cero();
        for (PagoFacturable pago : pagos) {
            suma = suma.add(pago.total());
        }
        return EscalasSri.aMonetario(suma);
    }

    /**
     * Comprueba que las formas de pago cubren exactamente el importe total.
     *
     * @throws IllegalArgumentException si la suma difiere del importe total.
     */
    public void validarPagos(TotalesFactura totales, List<PagoFacturable> pagos) {
        if (totales == null) {
            throw new IllegalArgumentException("Los totales de la factura son obligatorios.");
        }
        BigDecimal suma = sumarPagos(pagos);
        if (suma.compareTo(totales.importeTotal()) != 0) {
            throw new IllegalArgumentException(
                    "La suma de las formas de pago (" + suma + ") no coincide con el importe total ("
                            + totales.importeTotal() + ").");
        }
    }

    private static BigDecimal cero() {
        return BigDecimal.ZERO.setScale(EscalasSri.ESCALA_MONETARIA);
    }
}
