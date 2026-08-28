package com.biopet.facturacion.entity;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.entity.converter.AmbienteSriConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Contador fiscal de un punto de emision EN UN AMBIENTE concreto.
 *
 * <p>La unicidad {@code (punto_emision_id, ambiente)} es lo que permite que
 * 001-001-000000001 exista a la vez en PRUEBAS y en PRODUCCION sin colisionar:
 * son dos numeraciones legales distintas.
 *
 * <p>No confundir {@code ultimoSecuencial} con la PK: la PK es un identificador
 * interno generado por una secuencia de PostgreSQL; el secuencial fiscal es un
 * numero legal, contiguo y sin huecos, que solo puede avanzar de uno en uno.
 *
 * <p><b>Fase 4A no incrementa nada.</b> Esta clase es unicamente el modelo
 * persistente. La reserva concurrente del siguiente numero (bloqueo
 * pesimista, {@code SELECT ... FOR UPDATE}) es Fase 4B y no existe todavia: no
 * hay {@code @Lock}, ni {@code @Version}, ni servicio que lo mueva.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "secuencial_emision")
public class SecuencialEmision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "punto_emision_id", nullable = false)
    private PuntoEmision puntoEmision;

    @Convert(converter = AmbienteSriConverter.class)
    @Column(nullable = false)
    private AmbienteSri ambiente;

    /** 0 = todavia no se emitio nada. Tope 999999999 (9 digitos de la clave). */
    @Column(name = "ultimo_secuencial", nullable = false)
    private Long ultimoSecuencial;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (creadoEn == null) creadoEn = now;
        if (actualizadoEn == null) actualizadoEn = now;
        if (ultimoSecuencial == null) ultimoSecuencial = 0L;
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = Instant.now();
    }
}
