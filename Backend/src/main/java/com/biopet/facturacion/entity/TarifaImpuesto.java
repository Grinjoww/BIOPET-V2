package com.biopet.facturacion.entity;

import com.biopet.facturacion.domain.CodigoImpuestoSri;
import com.biopet.facturacion.entity.converter.CodigoImpuestoSriConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Porcentaje tributario vigente en un intervalo de fechas.
 *
 * <p>No es una "tarifa veterinaria" ni un precio: es la configuracion del par
 * (codigo de impuesto, codigo de porcentaje) del catalogo del SRI con el
 * porcentaje que estuvo o esta vigente. La tabla es HISTORICA: cuando cambia un
 * porcentaje se cierra la fila vigente con {@code vigenteHasta} y se inserta
 * otra, en lugar de sobrescribir.
 *
 * <p>Consecuencia buscada: una factura ya emitida nunca cambia de importe,
 * porque su tarifa quedo congelada en el detalle. La resolucion "que tarifa
 * aplica en esta fecha" es responsabilidad del futuro servicio de emision; esta
 * fase solo provee el modelo.
 *
 * <p>En produccion la tabla nace VACIA: no se siembra ningun porcentaje ni
 * ninguna fecha de vigencia.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tarifa_impuesto")
public class TarifaImpuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = CodigoImpuestoSriConverter.class)
    @Column(name = "codigo_impuesto", nullable = false, length = 2)
    private CodigoImpuestoSri codigoImpuesto;

    /**
     * Codigo de porcentaje del SRI (TABLA 17). Se guarda como cadena y no como
     * enum: el catalogo cambia con la normativa, y fijar aqui una lista cerrada
     * obligaria a una migracion cada vez que el SRI publique un valor nuevo. La
     * BD solo comprueba que sean 1 o 2 digitos.
     */
    @Column(name = "codigo_porcentaje", nullable = false, length = 2)
    private String codigoPorcentaje;

    @Column(nullable = false, length = 100)
    private String descripcion;

    /** Porcentaje, no fraccion: 15.00 significa 15%. XSD: totalDigits=4, fractionDigits=2. */
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal tarifa;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    /** Null = vigente indefinidamente. */
    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

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
