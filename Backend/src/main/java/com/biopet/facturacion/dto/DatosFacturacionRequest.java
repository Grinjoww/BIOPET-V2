package com.biopet.facturacion.dto;

import com.biopet.facturacion.entity.TipoIdentificacionSri;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entrada de alta/edicion de una identidad tributaria (cierra el hueco
 * operativo de la Fase 8A). Se usa igual para {@code POST} y {@code PUT}.
 *
 * <p>Deliberadamente NO incluye {@code usuarioId} -viaja en el path y lo
 * resuelve/valida {@code DatosFacturacionService}, nunca el cliente- ni
 * {@code predeterminado}: ese cambio de estado tiene su propio endpoint
 * ({@code PATCH .../predeterminado}) para que activar un perfil como
 * predeterminado sea siempre una operacion explicita y atomica, nunca un efecto
 * secundario de editar otro campo.
 *
 * <p>Editar estos datos NO altera ninguna factura ya emitida: son la FUENTE
 * desde la que {@code FacturaBorradorService#seleccionarComprador} copia un
 * snapshot al borrador, y ese snapshot vive en {@code Factura}, no aqui.
 */
public record DatosFacturacionRequest(
        @NotNull TipoIdentificacionSri tipoIdentificacion,
        @NotBlank @Size(max = 20) String identificacion,
        @NotBlank @Size(max = 300) String razonSocial,
        @Size(max = 300) String direccion,
        @Size(max = 20) String telefono,
        @Email @Size(max = 255) String emailFacturacion
) {
}
