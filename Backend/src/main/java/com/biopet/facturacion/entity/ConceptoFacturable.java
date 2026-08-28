package com.biopet.facturacion.entity;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.entity.converter.CodigoImpuestoSriConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Catalogo interno de lo que BIOPET puede facturar.
 *
 * <p>Guarda el PAR de codigos tributarios ({@code codigoImpuesto},
 * {@code codigoPorcentaje}) y NO una referencia a una fila concreta de
 * {@link TarifaImpuesto}. Es deliberado: {@code tarifa_impuesto} es una tabla
 * historica cuya fila vigente cambia con el tiempo, asi que apuntar a una fila
 * obligaria a reescribir el catalogo entero cada vez que cambiase un
 * porcentaje. El porcentaje aplicable se resuelve por fecha en el momento de
 * emitir y se congela en {@link FacturaDetalle}.
 *
 * <p>{@code precioUnitario} y {@code descripcion} son el valor ACTUAL del
 * catalogo, no el facturado: cambiarlos no altera ninguna factura ya emitida.
 *
 * <p>En produccion la tabla nace VACIA: no se siembra ningun concepto ni
 * ningun precio.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "concepto_facturable")
public class ConceptoFacturable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unico entre conceptos ACTIVOS (indice unico parcial en V7). */
    @Column(nullable = false, length = 25)
    private String codigo;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoConceptoFacturable tipo;

    /** XSD: totalDigits=18, fractionDigits=6, igual que el precio del detalle. */
    @Column(name = "precio_unitario", nullable = false, precision = 18, scale = 6)
    private BigDecimal precioUnitario;

    @Convert(converter = CodigoImpuestoSriConverter.class)
    @Column(name = "codigo_impuesto", nullable = false, length = 2)
    private CodigoImpuestoSri codigoImpuesto;

    @Column(name = "codigo_porcentaje", nullable = false, length = 2)
    private String codigoPorcentaje;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (creadoEn == null) creadoEn = now;
        if (actualizadoEn == null) actualizadoEn = now;
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = Instant.now();
    }
}
