package com.biopet.facturacion.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Bitacora append-only de cada intento contra los web services del SRI.
 *
 * <p>En esta fase no la escribe nadie: existe para que el pipeline de recepcion
 * y autorizacion (fases posteriores) tenga donde dejar evidencia auditable de
 * que se pidio, que se respondio y cuanto tardo. Una fila por intento, nunca se
 * actualiza ni se borra; por eso solo tiene {@code creadoEn} y no
 * {@code actualizadoEn}.
 *
 * <h2>JSONB sin dependencias externas</h2>
 *
 * <p>{@code mensajes} guarda la respuesta del SRI (identificador, mensaje,
 * informacion adicional) tal cual, en una columna {@code JSONB} nativa. Se mapea
 * con {@code @JdbcTypeCode(SqlTypes.JSON)}, soporte propio de Hibernate 6: el
 * dialecto de PostgreSQL resuelve ese tipo a {@code jsonb} sin necesidad de
 * anadir ninguna libreria de terceros al pom.
 *
 * <p>Se declara como {@code String} y no como un POJO mapeado a proposito: el
 * formato exacto de la respuesta del SRI todavia no esta modelado, y una
 * bitacora de auditoria debe poder guardar lo que llego aunque no encaje en el
 * modelo que tengamos hoy. {@code JSONB} permite consultarla igualmente con los
 * operadores nativos de PostgreSQL.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "factura_eventos_sri")
public class FacturaEventoSri {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id", nullable = false)
    private Factura factura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperacionSri operacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoEventoSri resultado;

    /** JSONB. Null si la llamada no devolvio ningun cuerpo (p. ej. un timeout). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String mensajes;

    /** Duracion de la llamada en milisegundos. */
    @Column(name = "duracion_ms")
    private Long duracionMs;

    /** Numero de intento, empezando en 1. */
    @Column(nullable = false)
    private Integer intento;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void prePersist() {
        if (creadoEn == null) creadoEn = Instant.now();
    }
}
