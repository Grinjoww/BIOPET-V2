package com.biopet.facturacion.entity;

import com.biopet.entity.Usuario;
import com.biopet.facturacion.entity.converter.TipoIdentificacionSriConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Identidad TRIBUTARIA de un cliente, separada por completo de {@link Usuario}.
 *
 * <p>{@code Usuario} sigue siendo identidad y autenticacion (email, hash de
 * contrasena, rol) y esta fase no le anade ni una columna. La relacion es 1:N a
 * proposito: la misma persona puede facturar a nombre propio con cedula y a
 * nombre de su empresa con RUC, y ambas identidades deben poder coexistir.
 *
 * <p>Un usuario no puede tener dos identidades activas marcadas como
 * predeterminadas: lo garantiza el indice unico parcial
 * {@code idx_datos_facturacion_predeterminado_unico} de PostgreSQL, no el
 * codigo Java.
 *
 * <p>Estos datos son la FUENTE desde la que se copia el snapshot del comprador
 * al emitir. Editarlos despues no altera ninguna factura ya emitida.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "datos_facturacion")
public class DatosFacturacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Convert(converter = TipoIdentificacionSriConverter.class)
    @Column(name = "tipo_identificacion", nullable = false, length = 2)
    private TipoIdentificacionSri tipoIdentificacion;

    @Column(nullable = false, length = 20)
    private String identificacion;

    @Column(name = "razon_social", nullable = false, length = 300)
    private String razonSocial;

    @Column(length = 300)
    private String direccion;

    @Column(length = 20)
    private String telefono;

    /** Correo al que se envia el comprobante; puede diferir del de la cuenta. */
    @Column(name = "email_facturacion", length = 255)
    private String emailFacturacion;

    @Column(nullable = false)
    private boolean predeterminado;

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
