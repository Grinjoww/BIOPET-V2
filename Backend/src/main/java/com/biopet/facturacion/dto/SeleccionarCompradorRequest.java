package com.biopet.facturacion.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Elige, por id, la identidad tributaria ya registrada que se copiara al
 * snapshot del comprador. El cliente NUNCA envia razon social, identificacion
 * ni direccion sueltas: solo selecciona ENTRE datos de facturacion que ya
 * existen y le pertenecen -eso lo comprueba
 * {@link com.biopet.facturacion.service.FacturaBorradorService#seleccionarComprador}-.
 * Crear/editar esos datos de facturacion es la Fase 8B.
 */
public record SeleccionarCompradorRequest(@NotNull Long datosFacturacionId) {
}
