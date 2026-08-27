package com.biopet.facturacion.domain;

import java.math.BigDecimal;

/**
 * Una linea de factura tal como entra al calculo: matematica pura, sin ids JPA,
 * sin relaciones y sin origen clinico.
 *
 * <p>La tarifa llega como <b>dato</b>. Esta clase no decide que tarifa
 * corresponde a un servicio veterinario ni a una vacuna: esa resolucion es
 * responsabilidad del catalogo tributario (tabla {@code TarifaImpuesto}, fase
 * posterior), que la resolvera por fecha de vigencia y la congelara como
 * snapshot en el detalle. Aqui solo se aplica lo recibido.
 *
 * <p>{@code codigoPorcentaje} se modela como cadena y no como enum a proposito:
 * la TABLA 17 de la ficha ya crecio en el tiempo (el codigo 10 = 13% se agrego
 * despues) y el XSD lo declara {@code [0-9]+} de hasta 4 caracteres. Cerrarlo
 * en un enum obligaria a recompilar ante cada decreto.
 */
public record LineaFacturable(
        String codigoPrincipal,
        String descripcion,
        BigDecimal cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuento,
        CodigoImpuestoSri codigoImpuesto,
        String codigoPorcentaje,
        BigDecimal tarifa
) {

    private static final int MAX_CODIGO_PRINCIPAL = 25;
    private static final int MAX_DESCRIPCION = 300;
    private static final int MAX_CODIGO_PORCENTAJE = 4;

    public LineaFacturable {
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripcion de la linea es obligatoria.");
        }
        if (descripcion.length() > MAX_DESCRIPCION) {
            throw new IllegalArgumentException(
                    "La descripcion admite como maximo " + MAX_DESCRIPCION + " caracteres.");
        }
        if (codigoPrincipal != null && codigoPrincipal.length() > MAX_CODIGO_PRINCIPAL) {
            throw new IllegalArgumentException(
                    "El codigo principal admite como maximo " + MAX_CODIGO_PRINCIPAL + " caracteres.");
        }
        if (codigoImpuesto == null) {
            throw new IllegalArgumentException("El codigo de impuesto es obligatorio.");
        }
        if (codigoPorcentaje == null || codigoPorcentaje.isBlank()
                || codigoPorcentaje.length() > MAX_CODIGO_PORCENTAJE) {
            throw new IllegalArgumentException(
                    "El codigo de porcentaje es obligatorio y admite como maximo "
                            + MAX_CODIGO_PORCENTAJE + " caracteres.");
        }
        for (int i = 0; i < codigoPorcentaje.length(); i++) {
            char caracter = codigoPorcentaje.charAt(i);
            if (caracter < '0' || caracter > '9') {
                throw new IllegalArgumentException(
                        "El codigo de porcentaje solo admite digitos; se recibio \"" + codigoPorcentaje + "\".");
            }
        }

        EscalasSri.exigirNoNegativo(cantidad, "La cantidad");
        EscalasSri.exigirEscalaMaxima(cantidad, EscalasSri.ESCALA_CANTIDAD, "La cantidad");
        EscalasSri.exigirNoNegativo(precioUnitario, "El precio unitario");
        EscalasSri.exigirEscalaMaxima(precioUnitario, EscalasSri.ESCALA_CANTIDAD, "El precio unitario");
        EscalasSri.exigirNoNegativo(descuento, "El descuento");
        EscalasSri.exigirEscalaMaxima(descuento, EscalasSri.ESCALA_MONETARIA, "El descuento");
        EscalasSri.exigirNoNegativo(tarifa, "La tarifa");
        EscalasSri.exigirEscalaMaxima(tarifa, EscalasSri.ESCALA_TARIFA, "La tarifa");

        BigDecimal bruto = cantidad.multiply(precioUnitario);
        if (descuento.compareTo(bruto) > 0) {
            throw new IllegalArgumentException(
                    "El descuento (" + descuento + ") no puede superar el importe bruto de la linea (" + bruto + ").");
        }
    }

    /** cantidad x precioUnitario, sin redondear: el producto exacto. */
    public BigDecimal importeBruto() {
        return cantidad.multiply(precioUnitario);
    }

    /** Clave de agrupacion tributaria: codigo de impuesto + codigo de porcentaje. */
    public String claveImpuesto() {
        return codigoImpuesto.codigo() + "-" + codigoPorcentaje;
    }
}
