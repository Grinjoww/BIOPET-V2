package com.biopet.facturacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entrada de {@code PUT /api/facturacion/emisor} (ADMIN).
 *
 * <p>BIOPET modela una sola clinica: no hay {@code POST}, este PUT hace
 * "upsert" de la unica fila de {@code emisor_fiscal} -la crea si todavia no
 * existe, la actualiza si ya existe-. Nunca crea una segunda fila mientras la
 * primera siga existiendo, aunque este inactiva: por eso {@code activo} SI
 * viaja aqui -es la unica via REST para reactivarla- en vez de un
 * {@code DELETE} fisico, que esta prohibido.
 *
 * <p>No incluye ni podria incluir el certificado .p12, su contrasena, ninguna
 * ruta de despliegue ni el ambiente SRI: eso sigue siendo exclusivamente
 * configuracion de servidor, fuera de BD y de este DTO.
 *
 * <p>Cambiar estos datos NO altera ninguna factura ya emitida: {@code
 * FacturaEmisionService} congela su propio snapshot del emisor al emitir (ver
 * {@code congelarEmisor}) y nunca vuelve a leer esta tabla despues.
 */
public record EmisorFiscalRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{13}$") String ruc,
        @NotBlank @Size(max = 300) String razonSocial,
        @Size(max = 300) String nombreComercial,
        @NotBlank @Size(max = 300) String direccionMatriz,
        boolean obligadoContabilidad,
        @Size(max = 13) String contribuyenteEspecial,
        boolean rimpe,
        @Size(max = 20) String agenteRetencionResolucion,
        @NotNull Boolean activo
) {
}
