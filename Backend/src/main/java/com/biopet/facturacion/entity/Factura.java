package com.biopet.facturacion.entity;

import com.biopet.entity.Mascota;
import com.biopet.entity.Usuario;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.entity.converter.AmbienteSriConverter;
import com.biopet.facturacion.entity.converter.TipoIdentificacionSriConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cabecera de una factura: contexto interno + snapshots fiscales + estado SRI.
 *
 * <h2>Por que no hay campo {@code activo}</h2>
 *
 * <p>El resto de entidades operativas de BIOPET usa baja logica. Una factura no,
 * y es una decision consciente:
 *
 * <ul>
 *   <li>Un comprobante autorizado existe en los sistemas del SRI y en la
 *       contabilidad del contribuyente. No puede "desaparecer", y una bandera
 *       que sugiera lo contrario es enganosa.</li>
 *   <li>{@code activo} seria una segunda fuente de verdad sobre el ciclo de
 *       vida, en conflicto con {@link EstadoFactura}.</li>
 *   <li>El riesgo es concreto: el patron {@code findAll...ActivoTrue} esta por
 *       todos los repositories del proyecto. Copiarlo por inercia en un reporte
 *       fiscal ocultaria facturas AUTORIZADAS de un cuadre tributario sin que
 *       nada fallase.</li>
 * </ul>
 *
 * <p>El ciclo de vida lo gobierna {@code estado} y nada mas. Lo unico
 * legitimamente eliminable es un BORRADOR, que nunca consumio numeracion
 * fiscal; esa decision pertenece al futuro servicio y esta fase no implementa
 * ningun borrado.
 *
 * <h2>Ownership</h2>
 *
 * <p>{@code usuario} es el propietario FUNCIONAL en BIOPET (quien ve la factura
 * en "mis facturas"), que no tiene por que ser el receptor tributario: el dueno
 * de la mascota puede pedir la factura a nombre de su empresa. El receptor real
 * es el snapshot {@code comprador*}.
 *
 * <h2>Snapshots</h2>
 *
 * <p>Los campos {@code comprador*} y {@code emisor*} se copian al emitir y no se
 * derivan nunca de las relaciones. Si manana cambia el RUC del emisor o la
 * direccion del cliente, esta factura no cambia. Las relaciones son solo
 * contexto y trazabilidad.
 *
 * <h2>Numeracion</h2>
 *
 * <p>Todo el bloque de numeracion es nullable porque un BORRADOR debe poder
 * guardarse incompleto. Nada de esto se genera aqui: la clave la compone
 * {@code ClaveAccesoGenerator} (Fase 2) y la reserva concurrente del secuencial
 * es Fase 4B.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "facturas")
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ------------------------------------------------------------------
    // Identidad interna
    // ------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Opcional: una factura puede ser solo de productos, sin contexto clinico. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mascota_id")
    private Mascota mascota;

    /** Nulo mientras es BORRADOR: aun no se decidio desde que punto emitir. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "punto_emision_id")
    private PuntoEmision puntoEmision;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoFactura estado;

    // ------------------------------------------------------------------
    // Numeracion fiscal (snapshot; nulo hasta emitir)
    // ------------------------------------------------------------------

    @Convert(converter = AmbienteSriConverter.class)
    @Column(name = "ambiente")
    private AmbienteSri ambiente;

    @Column(length = 3)
    private String establecimiento;

    /**
     * Codigo de 3 digitos del punto de emision, congelado. Se llama
     * {@code puntoEmisionCodigo} y no {@code puntoEmision} porque ese nombre ya
     * lo ocupa la RELACION de arriba: uno es trazabilidad (puede apuntar a un
     * punto que luego se renombre) y el otro es el valor que viajo en la clave
     * de acceso.
     */
    @Column(name = "punto_emision", length = 3)
    private String puntoEmisionCodigo;

    /** 1..999999999. Se formatea a 9 digitos al componer la clave de acceso. */
    @Column
    private Long secuencial;

    @Column(name = "codigo_numerico", length = 8)
    private String codigoNumerico;

    /** 49 digitos. Unica cuando existe. */
    @Column(name = "clave_acceso", length = 49)
    private String claveAcceso;

    // ------------------------------------------------------------------
    // Estado frente al SRI (el pipeline que lo alimenta es de una fase posterior)
    // ------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_recepcion", length = 20)
    private EstadoRecepcionSri estadoRecepcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_autorizacion", length = 3)
    private EstadoAutorizacionSri estadoAutorizacion;

    @Column(name = "numero_autorizacion", length = 49)
    private String numeroAutorizacion;

    @Column(name = "fecha_autorizacion")
    private Instant fechaAutorizacion;

    /** Momento a partir del cual el futuro reintento puede volver a consultar. */
    @Column(name = "proximo_intento_en")
    private Instant proximoIntentoEn;

    /**
     * Contador de intentos de autorizacion. Vive en la cabecera, y no solo
     * derivado de {@code factura_eventos_sri}, porque es el dato que el futuro
     * scheduler necesita leer y comparar en cada barrido: calcularlo con un
     * COUNT sobre la bitacora en cada pasada seria caro y no aporta nada. La
     * bitacora sigue siendo la evidencia detallada.
     */
    @Column(name = "intentos_autorizacion", nullable = false)
    private Integer intentosAutorizacion;

    // ------------------------------------------------------------------
    // Snapshot del comprador
    // ------------------------------------------------------------------

    @Convert(converter = TipoIdentificacionSriConverter.class)
    @Column(name = "comprador_tipo_identificacion", length = 2)
    private TipoIdentificacionSri compradorTipoIdentificacion;

    @Column(name = "comprador_identificacion", length = 20)
    private String compradorIdentificacion;

    @Column(name = "comprador_razon_social", length = 300)
    private String compradorRazonSocial;

    @Column(name = "comprador_direccion", length = 300)
    private String compradorDireccion;

    @Column(name = "comprador_email", length = 255)
    private String compradorEmail;

    @Column(name = "comprador_telefono", length = 20)
    private String compradorTelefono;

    // ------------------------------------------------------------------
    // Snapshot del emisor
    // ------------------------------------------------------------------

    @Column(name = "emisor_ruc", length = 13)
    private String emisorRuc;

    @Column(name = "emisor_razon_social", length = 300)
    private String emisorRazonSocial;

    @Column(name = "emisor_nombre_comercial", length = 300)
    private String emisorNombreComercial;

    @Column(name = "emisor_direccion_matriz", length = 300)
    private String emisorDireccionMatriz;

    @Column(name = "emisor_direccion_establecimiento", length = 300)
    private String emisorDireccionEstablecimiento;

    @Column(name = "emisor_obligado_contabilidad")
    private Boolean emisorObligadoContabilidad;

    @Column(name = "emisor_contribuyente_especial", length = 13)
    private String emisorContribuyenteEspecial;

    @Column(name = "emisor_rimpe")
    private Boolean emisorRimpe;

    @Column(name = "emisor_agente_retencion_resolucion", length = 20)
    private String emisorAgenteRetencionResolucion;

    // ------------------------------------------------------------------
    // Totales (XSD: totalDigits=14, fractionDigits=2 -- ver EscalasSri)
    // ------------------------------------------------------------------

    @Column(name = "total_sin_impuestos", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalSinImpuestos;

    @Column(name = "total_descuento", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDescuento;

    @Column(name = "total_impuestos", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalImpuestos;

    @Column(name = "importe_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal importeTotal;

    @Column(length = 15)
    private String moneda;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    // ------------------------------------------------------------------
    // Composicion
    // ------------------------------------------------------------------
    //
    // cascade = {PERSIST, MERGE} y NADA mas, a proposito:
    //   * sin CascadeType.ALL y sin REMOVE: ningun borrado debe propagarse
    //     hacia lineas de un comprobante fiscal;
    //   * sin orphanRemoval: quitar un elemento de la lista en memoria no
    //     puede traducirse en un DELETE implicito.
    // La BD refuerza lo mismo: las FK de las tablas hijas NO llevan
    // ON DELETE CASCADE, asi que una factura con lineas no se puede borrar.
    // Documentos y eventos NO se exponen como coleccion: son append-only y se
    // consultan por su repository, que es el acceso natural y evita cargar
    // binarios al tocar la cabecera.

    @OneToMany(mappedBy = "factura", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("linea ASC")
    @Builder.Default
    private List<FacturaDetalle> detalles = new ArrayList<>();

    @OneToMany(mappedBy = "factura", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<FacturaPago> pagos = new ArrayList<>();

    /** Anade una linea manteniendo los dos extremos de la relacion coherentes. */
    public void agregarDetalle(FacturaDetalle detalle) {
        detalle.setFactura(this);
        detalles.add(detalle);
    }

    /** Anade un pago manteniendo los dos extremos de la relacion coherentes. */
    public void agregarPago(FacturaPago pago) {
        pago.setFactura(this);
        pagos.add(pago);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (creadoEn == null) creadoEn = now;
        if (actualizadoEn == null) actualizadoEn = now;
        if (estado == null) estado = EstadoFactura.BORRADOR;
        if (intentosAutorizacion == null) intentosAutorizacion = 0;
        if (totalSinImpuestos == null) totalSinImpuestos = BigDecimal.ZERO;
        if (totalDescuento == null) totalDescuento = BigDecimal.ZERO;
        if (totalImpuestos == null) totalImpuestos = BigDecimal.ZERO;
        if (importeTotal == null) importeTotal = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = Instant.now();
    }
}
