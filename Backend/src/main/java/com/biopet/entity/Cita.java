package com.biopet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Agendamiento previo de una atención veterinaria (mascota + veterinario + fecha/hora).
 * No confundir con "Consulta" (registro clínico posterior a la atención, módulo
 * separado a cargo de otro integrante del equipo).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "citas")
public class Cita {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mascota_id", nullable = false)
    private Mascota mascota;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veterinario_id", nullable = false)
    private Usuario veterinario;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCita estado;

    @Column(length = 255)
    private String motivo;

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
        activo = true;
        if (estado == null) estado = EstadoCita.PROGRAMADA;
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = Instant.now();
    }
}
