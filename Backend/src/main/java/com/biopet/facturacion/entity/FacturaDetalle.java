package com.biopet.facturacion.entity;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.entity.converter.CodigoImpuestoSriConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Una linea del comprobante. TODOS sus campos economicos son SNAPSHOT.
 *
 * <p>La descripcion, el precio unitario y la tarifa quedan congelados en el
 * momento de facturar. Nunca deben leerse del catalogo en tiempo de consulta:
 * si manana sube el precio de una vacuna o cambia el porcentaje de un impuesto,
 * esta linea debe seguir diciendo exactamente lo que se facturo. Por eso
 * {@code conceptoFacturable} es nullable y es solo trazabilidad: una linea
 * puede describir algo que no esta en el catalogo, y el concepto puede darse de
 * baja despues sin afectar a la factura.
 *
 * <p>Escalas segun el XSD oficial (ver {@code EscalasSri} de la Fase 2):
 * cantidad y precio unitario con 6 decimales; importes con 2; tarifa con 2.
 *
 * <p>El origen clinico ({@code origenTipo} + {@code origenId}) es una referencia
 * debil deliberada: sin FK polimorfica, para no acoplar el modulo fiscal a las
 * tablas clinicas, que esta fase no modifica.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "factura_detalles")
public class FacturaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id", nullable = false)
    private Factura factura;

    /** Numero de linea dentro de la factura, empezando en 1. Unico por factura. */
    @Column(nullable = false)
    private Integer linea;

    /** Solo trazabilidad hacia el catalogo. Nullable: la linea vive sin el. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concepto_facturable_id")
    private ConceptoFacturable conceptoFacturable;

    @Column(name = "codigo_principal", nullable = false, length = 25)
    private String codigoPrincipal;

    @Column(name = "codigo_auxiliar", length = 25)
    private String codigoAuxiliar;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 18, scale = 6)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal descuento;

    @Column(name = "precio_total_sin_impuesto", nullable = false, precision = 14, scale = 2)
    private BigDecimal precioTotalSinImpuesto;

    @Convert(converter = CodigoImpuestoSriConverter.class)
    @Column(name = "impuesto_codigo", nullable = false, length = 2)
    private CodigoImpuestoSri impuestoCodigo;

    @Column(name = "impuesto_codigo_porcentaje", nullable = false, length = 2)
    private String impuestoCodigoPorcentaje;

    /** Porcentaje congelado: 15.00 significa 15%. */
    @Column(name = "impuesto_tarifa", nullable = false, precision = 4, scale = 2)
    private BigDecimal impuestoTarifa;

    @Column(name = "base_imponible", nullable = false, precision = 14, scale = 2)
    private BigDecimal baseImponible;

    @Column(name = "impuesto_valor", nullable = false, precision = 14, scale = 2)
    private BigDecimal impuestoValor;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen_tipo", length = 20)
    private OrigenDetalleFactura origenTipo;

    /** Id del registro clinico de origen. Sin FK: referencia debil deliberada. */
    @Column(name = "origen_id")
    private Long origenId;

    @PrePersist
    void prePersist() {
        if (descuento == null) descuento = BigDecimal.ZERO;
    }
}
