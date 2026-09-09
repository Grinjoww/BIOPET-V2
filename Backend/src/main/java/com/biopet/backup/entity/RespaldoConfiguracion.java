package com.biopet.backup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Fila UNICA de configuracion del modulo de respaldos (ver V10, que
 * reemplaza en esta tabla el intervalo de V9 por dia+hora). {@code id}
 * siempre vale 1 -no es BIGSERIAL: lo fija la aplicacion, la base solo lo
 * refuerza con chk_respaldo_configuracion_id_singleton.
 *
 * <p>La programacion es semanal y explicita: "todos los {@code diaSemana} a
 * las {@code hora}" -sin exponer nunca una expresion CRON al ADMIN (pedido
 * explicito del docente). {@link DayOfWeek} se reutiliza tal cual -MONDAY..
 * SUNDAY son exactamente los valores pedidos- en vez de definir un enum
 * propio duplicado.
 *
 * <p>actualizadoEn/actualizadoPor los escribe EXCLUSIVAMENTE
 * RespaldoConfiguracionService.guardar(...) -quien cambio
 * activo/diaSemana/hora y cuando-. RespaldoEjecucionService, tras cada
 * intento de respaldo, solo toca ultimoRespaldoEn/proximoRespaldoEn a
 * traves de RespaldoConfiguracionService.marcarEjecutado(...), sin pasar
 * por aqui esos dos campos de auditoria.
 */
@Entity
@Table(name = "respaldo_configuracion")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespaldoConfiguracion {

    /** Siempre 1L. Ver chk_respaldo_configuracion_id_singleton en V9. */
    @Id
    private Long id;

    @Column(nullable = false)
    private boolean activo;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DayOfWeek diaSemana;

    /** Hora del dia (zona horaria: ver RespaldoConfiguracionService.ZONA_HORARIA). Sin segundos para la UI (HH:mm). */
    @Column(nullable = false)
    private LocalTime hora;

    @Column(name = "ultimo_respaldo_en")
    private Instant ultimoRespaldoEn;

    @Column(name = "proximo_respaldo_en")
    private Instant proximoRespaldoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "actualizado_por", length = 255)
    private String actualizadoPor;
}
