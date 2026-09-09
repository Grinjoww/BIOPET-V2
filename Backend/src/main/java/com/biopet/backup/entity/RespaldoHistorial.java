package com.biopet.backup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Una fila por intento de respaldo (ver V9, tabla respaldo_historial). Se
 * crea en estado EN_PROCESO y se actualiza una unica vez, al terminar, a
 * EXITOSO o FALLIDO -nunca se borra ni se reutiliza una fila para un
 * reintento.
 */
@Entity
@Table(name = "respaldo_historial")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespaldoHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoRespaldo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoRespaldo estado;

    @Column(name = "iniciado_en", nullable = false)
    private Instant iniciadoEn;

    @Column(name = "finalizado_en")
    private Instant finalizadoEn;

    @Column(name = "duracion_ms")
    private Long duracionMs;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    /**
     * SIEMPRE una categoria curada por RespaldoEjecucionService (timeout,
     * codigo de salida, error de E/S...), nunca el stderr crudo de pg_dump.
     * Ver comentario de la columna en V9.
     */
    @Column(name = "mensaje_error_seguro", length = 500)
    private String mensajeErrorSeguro;

    /** NULL para AUTOMATICO. Email del ADMIN para MANUAL. */
    @Column(name = "ejecutado_por", length = 255)
    private String ejecutadoPor;
}
