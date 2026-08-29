package com.biopet.facturacion.dto;

import com.biopet.facturacion.entity.TipoIdentificacionSri;

/**
 * Vista compacta de una identidad tributaria del usuario. No incluye
 * {@code usuarioId}: viaja siempre en el path ({@code
 * /api/usuarios/{usuarioId}/datos-facturacion}), no en el cuerpo.
 */
public record DatosFacturacionResponse(
        Long id,
        TipoIdentificacionSri tipoIdentificacion,
        String identificacion,
        String razonSocial,
        String direccion,
        String telefono,
        String emailFacturacion,
        boolean predeterminado,
        boolean activo
) {
}
