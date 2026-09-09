package com.biopet.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Un evento de auditoria (ver V10, tabla auditoria_evento). Dos origenes,
 * la MISMA tabla:
 * <ul>
 *   <li>Generico: AuditoriaHttpFilter registra toda peticion AUTENTICADA
 *       POST/PUT/PATCH/DELETE contra {@code /api/**} (salvo /api/auth/** y
 *       /api/admin/respaldos/**, cubiertos abajo con eventos propios) sin
 *       tocar ningun controller existente -{@code accion} es el metodo HTTP,
 *       {@code modulo} el primer segmento de la ruta.</li>
 *   <li>Nombrado: eventos de negocio explicitos -los de autenticacion que ya
 *       emitia AuthenticationAuditService (LOGIN_SUCCESS, LOGIN_FAILURE...,
 *       reutilizados tal cual, no duplicados con otro nombre) y los 5 de
 *       respaldos (BACKUP_CONFIG_UPDATED, BACKUP_MANUAL_REQUESTED,
 *       BACKUP_AUTOMATIC_STARTED, BACKUP_COMPLETED, BACKUP_FAILED).</li>
 * </ul>
 *
 * <p>Deliberadamente NO guarda nada del cuerpo de la peticion: ni
 * contrasenas, ni JWT, ni el certificado, ni XML fiscal, ni el body
 * completo. Solo metadatos (quien, que accion, en que modulo/recurso, con
 * que metodo/resultado) -ver AuditoriaService.registrar, el UNICO punto de
 * escritura.
 */
@Entity
@Table(name = "auditoria_evento")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;

    /** NULL para eventos sin usuario identificable (p.ej. BACKUP_AUTOMATIC_STARTED, un intento de login fallido sin cuenta real). */
    @Column(name = "usuario_email", length = 255)
    private String usuarioEmail;

    /** Metodo HTTP (POST/PUT/PATCH/DELETE) para eventos genericos, o el nombre del evento de negocio (LOGIN_SUCCESS, BACKUP_COMPLETED...). */
    @Column(nullable = false, length = 60)
    private String accion;

    /** Primer segmento de la ruta (p.ej. "mascotas", "facturas") o "auth"/"admin/respaldos" para eventos nombrados. */
    @Column(nullable = false, length = 60)
    private String modulo;

    /** Ruta completa u otro detalle corto no sensible (p.ej. "/api/mascotas/5"). Nunca el cuerpo de la peticion. */
    @Column(length = 255)
    private String recurso;

    /** NULL cuando el evento no corresponde a una unica llamada HTTP (p.ej. BACKUP_COMPLETED, que ocurre en segundo plano). */
    @Column(name = "metodo_http", length = 10)
    private String metodoHttp;

    /** SUCCESS / FAILURE / BLOCKED -mismo vocabulario que ya usaba AuthenticationAuditService. */
    @Column(nullable = false, length = 20)
    private String resultado;

    /** Codigo de estado HTTP real cuando aplica (p.ej. 201, 403, 500). NULL para eventos sin respuesta HTTP asociada. */
    @Column(name = "status_http")
    private Integer statusHttp;
}
