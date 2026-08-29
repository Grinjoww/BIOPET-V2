package com.biopet.facturacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entrada para dar de alta un punto de emision (ADMIN).
 *
 * <p>{@code establecimiento} y {@code puntoEmision} son la identidad de la
 * serie fiscal (la "001-001" de la clave de acceso): se fijan SOLO al crear y
 * son inmutables despues -ver {@link ActualizarPuntoEmisionRequest}, que no los
 * incluye-. Cambiarlos en un punto que ya emitio comprobantes reescribiria en
 * silencio la serie de una numeracion que el SRI exige contigua.
 *
 * <p>Nunca se acepta aqui {@code ultimo_secuencial}, {@code siguiente_secuencial}
 * ni {@code ambiente}: esos siguen siendo exclusivamente del pipeline de
 * emision ({@code SecuencialEmision}), no de este catalogo.
 */
public record PuntoEmisionRequest(
        @NotNull Long emisorFiscalId,
        @NotBlank @Pattern(regexp = "^[0-9]{3}$") String establecimiento,
        @NotBlank @Pattern(regexp = "^[0-9]{3}$") String puntoEmision,
        @Size(max = 300) String direccionEstablecimiento
) {
}
