package com.biopet.facturacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Punto de emision de un emisor: el par establecimiento + punto que forma la
 * "serie" 001-001 de la clave de acceso.
 *
 * <p>NO guarda el ultimo secuencial. El contador depende del ambiente y vive en
 * {@link SecuencialEmision}, uno por (punto, ambiente), para que las
 * numeraciones de PRUEBAS y PRODUCCION sean independientes.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "punto_emision")
public class PuntoEmision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "emisor_fiscal_id", nullable = false)
    private EmisorFiscal emisorFiscal;

    /** Exactamente 3 digitos. */
    @Column(nullable = false, length = 3)
    private String establecimiento;

    /** Exactamente 3 digitos. */
    @Column(name = "punto_emision", nullable = false, length = 3)
    private String puntoEmision;

    @Column(name = "direccion_establecimiento", length = 300)
    private String direccionEstablecimiento;

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
