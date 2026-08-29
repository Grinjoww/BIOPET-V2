package com.biopet.facturacion.dto;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.entity.TipoConceptoFacturable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Entrada de alta/edicion de un concepto facturable (ADMIN).
 *
 * <p>Se usa igual para {@code POST} y {@code PUT}: los mismos campos de negocio
 * se crean y se editan juntos. Lo unico que NO viaja aqui es {@code activo}
 * -eso es {@code PATCH /estado}, un cambio de estado distinto de una edicion de
 * catalogo- ni ningun campo de auditoria.
 *
 * <p>Editar estos campos es seguro para facturas ya emitidas: {@code
 * FacturaDetalle} congela su propio snapshot (codigo, descripcion, precio,
 * impuesto) al facturar y nunca vuelve a leer el catalogo -ver su javadoc-.
 */
public record ConceptoFacturableRequest(
        @NotBlank @Size(max = 25) String codigo,
        @NotBlank @Size(max = 300) String descripcion,
        @NotNull TipoConceptoFacturable tipo,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 6) BigDecimal precioUnitario,
        @NotNull CodigoImpuestoSri codigoImpuesto,
        @NotBlank @Pattern(regexp = "^[0-9]{1,2}$") String codigoPorcentaje
) {
}
