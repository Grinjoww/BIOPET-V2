package com.biopet.facturacion.dto;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entrada para abrir una NUEVA vigencia de tarifa (ADMIN).
 *
 * <p>No existe un {@code PUT} de edicion: {@code tarifa_impuesto} es
 * historica a proposito (ver el javadoc de la entidad) y sobrescribir una fila
 * ya usada por una factura cambiaria en silencio un importe ya emitido. Lo
 * unico que se acepta por REST es crear la siguiente vigencia -{@code
 * TarifaImpuestoService} cierra automaticamente la vigencia abierta anterior
 * del mismo par, si la hay- y {@code PATCH /estado} para dar de baja logica.
 *
 * <p>{@code vigenteHasta} tampoco se acepta aqui: toda vigencia nueva nace
 * abierta ({@code null}); se cierra sola cuando se crea la siguiente.
 */
public record TarifaImpuestoRequest(
        @NotNull CodigoImpuestoSri codigoImpuesto,
        @NotBlank @Pattern(regexp = "^[0-9]{1,2}$") String codigoPorcentaje,
        @NotBlank @Size(max = 100) String descripcion,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 2, fraction = 2) BigDecimal tarifa,
        @NotNull LocalDate vigenteDesde
) {
}
