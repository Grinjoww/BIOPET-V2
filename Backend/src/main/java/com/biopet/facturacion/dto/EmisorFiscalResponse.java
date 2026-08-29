package com.biopet.facturacion.dto;

/**
 * Datos tributarios NO secretos del emisor. Nunca incluye -ni podria, la
 * entidad no los tiene- el certificado .p12, su contrasena, ninguna ruta de
 * despliegue ni el ambiente SRI: esos siguen siendo exclusivamente
 * configuracion de servidor.
 */
public record EmisorFiscalResponse(
        Long id,
        String ruc,
        String razonSocial,
        String nombreComercial,
        String direccionMatriz,
        boolean obligadoContabilidad,
        String contribuyenteEspecial,
        boolean rimpe,
        String agenteRetencionResolucion,
        boolean activo
) {
}
