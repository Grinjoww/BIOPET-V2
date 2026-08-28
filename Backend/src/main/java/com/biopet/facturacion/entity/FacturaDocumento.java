package com.biopet.facturacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Artefacto binario de una factura: el XML en sus tres estados y el RIDE.
 *
 * <h2>Por que {@code byte[]} sin {@code @Lob}</h2>
 *
 * <p>La columna es {@code BYTEA}. En el dialecto PostgreSQL de Hibernate 6, un
 * {@code byte[]} sin anotar resuelve a {@code bytea}, que es lo que queremos.
 * Anotarlo con {@code @Lob} lo cambiaria a {@code oid} (large object), y eso
 * trae tres problemas reales: el contenido deja de vivir en la tabla y pasa a
 * {@code pg_largeobject}, hace falta una transaccion abierta para leerlo, y
 * borrar la fila NO borra el objeto, con lo que se acumula basura invisible.
 * Para artefactos de unos pocos cientos de KB, {@code bytea} es la eleccion
 * correcta. Hay un test que lo comprueba contra el catalogo real de PostgreSQL,
 * no solo contra el DDL.
 *
 * <p>Hay como maximo un documento de cada tipo por factura
 * ({@code uq_factura_documentos_tipo}): el XML firmado de una factura es uno
 * solo.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "factura_documentos")
public class FacturaDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id", nullable = false)
    private Factura factura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDocumentoFactura tipo;

    /**
     * BYTEA. Deliberadamente sin {@code @Basic(fetch = LAZY)}: el proyecto no
     * activa el bytecode enhancement de Hibernate, asi que esa anotacion seria
     * un no-op que solo aparentaria una carga diferida que no ocurre. Para no
     * traer el binario cuando no hace falta, se consulta la cabecera y se pide
     * el documento por su repository solo cuando se necesita.
     */
    @Column(nullable = false)
    private byte[] contenido;

    /** SHA-256 del contenido, 64 hexadecimales en minuscula. */
    @Column(nullable = false, length = 64)
    private String sha256;

    /** Tamano en bytes del contenido, para no tener que leerlo solo para medirlo. */
    @Column(nullable = false)
    private Integer bytes;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void prePersist() {
        if (creadoEn == null) creadoEn = Instant.now();
    }
}
