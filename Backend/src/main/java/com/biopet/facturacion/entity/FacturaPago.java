package com.biopet.facturacion.entity;

import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.entity.converter.FormaPagoSriConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Una forma de pago del comprobante (bloque {@code <pagos>} del XML).
 *
 * <p>La regla {@code SUM(total de los pagos) == factura.importeTotal} NO se
 * implementa con un trigger: no es una invariante estructural de la fila sino
 * una invariante del documento completo, que solo tiene sentido comprobar
 * cuando la factura se emite. Un trigger por fila la haria imposible de
 * construir (la primera insercion ya la violaria). Corresponde al futuro
 * servicio de emision.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "factura_pagos")
public class FacturaPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id", nullable = false)
    private Factura factura;

    /** TABLA 24 de la Ficha v2.34; se persiste el codigo de dos caracteres. */
    @Convert(converter = FormaPagoSriConverter.class)
    @Column(name = "forma_pago", nullable = false, length = 2)
    private FormaPagoSri formaPago;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    /** Plazo del pago; solo aplica a formas de pago a credito. */
    @Column
    private Integer plazo;

    /** Unidad del plazo (por ejemplo "dias"). Null si no hay plazo. */
    @Column(name = "unidad_tiempo", length = 20)
    private String unidadTiempo;
}
