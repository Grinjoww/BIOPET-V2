package com.biopet.facturacion.service.command;

import com.biopet.facturacion.entity.OrigenDetalleFactura;

import java.math.BigDecimal;

/**
 * Una linea tal y como la pide quien llama.
 *
 * <p>Fijese en lo que NO tiene: ni precio, ni codigo de impuesto, ni tarifa, ni
 * descripcion. Todo eso lo pone el backend a partir del
 * {@code ConceptoFacturable} y del catalogo de tarifas. Es deliberado: cuando
 * exista el endpoint REST, un cliente no debe poder facturarse a si mismo una
 * consulta a 0.01 o con IVA 0% enviando un JSON distinto.
 *
 * @param conceptoFacturableId concepto activo del catalogo. Obligatorio.
 * @param cantidad             obligatoria, no negativa, hasta 6 decimales.
 * @param descuento            opcional ({@code null} = 0), hasta 2 decimales.
 * @param origenTipo           trazabilidad clinica opcional (CONSULTA/VACUNA/CITA).
 * @param origenId             id del registro clinico; va siempre junto a origenTipo.
 */
public record DetalleBorradorCommand(
        Long conceptoFacturableId,
        BigDecimal cantidad,
        BigDecimal descuento,
        OrigenDetalleFactura origenTipo,
        Long origenId
) {

    /** Atajo para la mayoria de lineas: sin descuento y sin origen clinico. */
    public static DetalleBorradorCommand de(Long conceptoFacturableId, BigDecimal cantidad) {
        return new DetalleBorradorCommand(conceptoFacturableId, cantidad, null, null, null);
    }
}
