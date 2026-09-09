package com.biopet.backup.service;

import com.biopet.audit.service.AuditoriaService;
import com.biopet.backup.dto.RespaldoConfiguracionRequest;
import com.biopet.backup.dto.RespaldoConfiguracionResponse;
import com.biopet.backup.entity.RespaldoConfiguracion;
import com.biopet.backup.repository.RespaldoConfiguracionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Duena exclusiva de la fila unica respaldo_configuracion (id=1). Ningun
 * otro servicio la escribe directamente: RespaldoEjecucionService pasa
 * siempre por {@link #marcarEjecutado(Instant)} tras cada intento de
 * respaldo, sin tocar actualizadoEn/actualizadoPor -esos dos solo cambian
 * cuando un ADMIN guarda la configuracion desde {@link #guardar}.
 *
 * <p>Programacion semanal simple ("todos los diaSemana a las hora"), sin
 * exponer CRON al ADMIN -pedido explicito del docente-. V9 tenia un
 * intervalo (valor+unidad); V10 lo reemplaza por dia+hora porque asi lo
 * pidio la correccion del docente (ver V10__respaldos_dia_hora_y_auditoria.sql).
 */
@Service
public class RespaldoConfiguracionService {

    private static final long ID_SINGLETON = 1L;

    /**
     * Zona horaria en la que se interpreta "hora" (HH:mm). Fija a Ecuador
     * -mismo pais que el resto del dominio fiscal de BIOPET (SRI, RUC)- y
     * sin horario de verano, para que "las 02:00" signifique siempre lo
     * mismo sin depender de la zona por defecto de la maquina donde corre
     * el backend.
     */
    private static final ZoneId ZONA_HORARIA = ZoneId.of("America/Guayaquil");

    private static final DayOfWeek DIA_POR_DEFECTO = DayOfWeek.MONDAY;
    private static final LocalTime HORA_POR_DEFECTO = LocalTime.of(2, 0);

    private final RespaldoConfiguracionRepository repository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    // @Autowired explicito: hay un segundo constructor (con Clock, para
    // tests) y sin esta anotacion Spring exige un constructor sin
    // argumentos en vez de elegir este entre los dos.
    @Autowired
    public RespaldoConfiguracionService(RespaldoConfiguracionRepository repository,
                                         AuditoriaService auditoriaService) {
        this(repository, auditoriaService, Clock.systemUTC());
    }

    RespaldoConfiguracionService(RespaldoConfiguracionRepository repository,
                                  AuditoriaService auditoriaService,
                                  Clock clock) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @Transactional
    public RespaldoConfiguracionResponse obtener() {
        return toResponse(obtenerOCrearEntidad());
    }

    @Transactional
    public RespaldoConfiguracionResponse guardar(RespaldoConfiguracionRequest request, String emailAdmin) {
        RespaldoConfiguracion configuracion = obtenerOCrearEntidad();
        Instant ahora = clock.instant();

        configuracion.setActivo(request.activo());
        configuracion.setDiaSemana(request.diaSemana());
        configuracion.setHora(request.hora());
        configuracion.setActualizadoEn(ahora);
        configuracion.setActualizadoPor(emailAdmin);
        configuracion.setProximoRespaldoEn(request.activo() ? calcularProximo(configuracion, ahora) : null);

        RespaldoConfiguracionResponse respuesta = toResponse(repository.save(configuracion));

        auditoriaService.registrar(emailAdmin, "BACKUP_CONFIG_UPDATED", "admin/respaldos",
                "activo=" + request.activo() + " diaSemana=" + request.diaSemana() + " hora=" + request.hora(),
                "PUT", "SUCCESS", 200);

        return respuesta;
    }

    /**
     * Llamado por RespaldoEjecucionService al terminar cualquier intento de
     * respaldo (exitoso o fallido: un pg_dump que falla repetidamente igual
     * debe reprogramarse hacia adelante, o el scheduler lo reintentaria en
     * cada revision sin esperar hasta el proximo dia/hora configurado).
     * Deliberadamente NO toca actualizadoEn/actualizadoPor.
     */
    @Transactional
    public void marcarEjecutado(Instant momento) {
        RespaldoConfiguracion configuracion = obtenerOCrearEntidad();
        configuracion.setUltimoRespaldoEn(momento);
        configuracion.setProximoRespaldoEn(configuracion.isActivo() ? calcularProximo(configuracion, momento) : null);
        repository.save(configuracion);
    }

    /**
     * Proxima ocurrencia de "diaSemana a las hora", estrictamente POSTERIOR
     * a {@code base} -si hoy es el dia configurado pero la hora ya paso (o
     * es exactamente ahora), salta a la semana siguiente en vez de devolver
     * un instante ya pasado que el scheduler dispararia de inmediato.
     */
    private Instant calcularProximo(RespaldoConfiguracion configuracion, Instant base) {
        ZonedDateTime baseZonificada = base.atZone(ZONA_HORARIA);
        ZonedDateTime candidato = baseZonificada
                .with(TemporalAdjusters.nextOrSame(configuracion.getDiaSemana()))
                .with(configuracion.getHora());
        if (!candidato.isAfter(baseZonificada)) {
            candidato = candidato.plusWeeks(1);
        }
        return candidato.toInstant();
    }

    /**
     * La fila se crea de forma perezosa (activo=false, lunes 02:00) en el
     * primer acceso -V9/V10 no siembran ningun dato, mismo criterio que
     * V7/V8 con sus catalogos fiscales.
     */
    private RespaldoConfiguracion obtenerOCrearEntidad() {
        return repository.findById(ID_SINGLETON).orElseGet(() -> repository.save(
                RespaldoConfiguracion.builder()
                        .id(ID_SINGLETON)
                        .activo(false)
                        .diaSemana(DIA_POR_DEFECTO)
                        .hora(HORA_POR_DEFECTO)
                        .ultimoRespaldoEn(null)
                        .proximoRespaldoEn(null)
                        .actualizadoEn(clock.instant())
                        .actualizadoPor(null)
                        .build()));
    }

    private RespaldoConfiguracionResponse toResponse(RespaldoConfiguracion c) {
        return new RespaldoConfiguracionResponse(
                c.isActivo(),
                c.getDiaSemana(),
                c.getHora(),
                c.getUltimoRespaldoEn(),
                c.getProximoRespaldoEn(),
                c.getActualizadoEn(),
                c.getActualizadoPor()
        );
    }
}
