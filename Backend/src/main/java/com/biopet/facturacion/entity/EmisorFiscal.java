package com.biopet.facturacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Datos tributarios NO secretos del contribuyente que emite los comprobantes.
 *
 * <p>Lo que deliberadamente NO vive aqui:
 * <ul>
 *   <li>el certificado .p12 y su contrasena: son secretos de despliegue, no
 *       datos de negocio, y no se guardan en ninguna tabla;</li>
 *   <li>el ambiente (pruebas/produccion): es configuracion de runtime. El
 *       contador de numeracion si depende del ambiente, y por eso vive en
 *       {@link SecuencialEmision}, no aqui.</li>
 * </ul>
 *
 * <p>El RUC se valida solo en su FORMA (13 digitos), igual que hace
 * {@code ClaveAccesoRequest} en el nucleo fiscal.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "emisor_fiscal")
public class EmisorFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 13)
    private String ruc;

    @Column(name = "razon_social", nullable = false, length = 300)
    private String razonSocial;

    @Column(name = "nombre_comercial", length = 300)
    private String nombreComercial;

    @Column(name = "direccion_matriz", nullable = false, length = 300)
    private String direccionMatriz;

    @Column(name = "obligado_contabilidad", nullable = false)
    private boolean obligadoContabilidad;

    /** Numero de resolucion de contribuyente especial; null si no lo es. */
    @Column(name = "contribuyente_especial", length = 13)
    private String contribuyenteEspecial;

    /**
     * Si el contribuyente esta acogido al regimen RIMPE. Se modela como
     * booleano y no como el texto de la leyenda ("CONTRIBUYENTE REGIMEN
     * RIMPE"...) porque la leyenda es una cadena de presentacion que se compone
     * al generar el XML; guardarla aqui mezclaria un dato de estado tributario
     * con su representacion.
     */
    @Column(nullable = false)
    private boolean rimpe;

    @Column(name = "agente_retencion_resolucion", length = 20)
    private String agenteRetencionResolucion;

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
