package com.biopet.backup.service;

import com.biopet.audit.service.AuditoriaService;
import com.biopet.backup.config.BackupProperties;
import com.biopet.backup.dto.RespaldoHistorialResponse;
import com.biopet.backup.entity.EstadoRespaldo;
import com.biopet.backup.entity.RespaldoHistorial;
import com.biopet.backup.entity.TipoRespaldo;
import com.biopet.backup.exception.RespaldoEnProcesoException;
import com.biopet.backup.pgdump.ConexionPostgres;
import com.biopet.backup.pgdump.JdbcPostgresUrlParser;
import com.biopet.backup.pgdump.PgDumpExecutionException;
import com.biopet.backup.pgdump.PgDumpExecutor;
import com.biopet.backup.pgdump.PgDumpParametros;
import com.biopet.backup.repository.RespaldoHistorialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orquesta un intento de respaldo de principio a fin: crea la fila
 * EN_PROCESO, dispara pg_dump en segundo plano (nunca en el hilo que
 * atiende la peticion HTTP ni en el hilo del scheduler) y deja la fila en
 * EXITOSO o FALLIDO al terminar.
 *
 * <p><b>Regla que gobierna toda la clase:</b> ningun fallo de aqui puede
 * tumbar BIOPET. El trabajo real corre en {@code executor} (un solo hilo
 * dedicado, ver BackupExecutorConfig) dentro de un try/catch que atrapa
 * TODO -incluyendo errores inesperados que no sean PgDumpExecutionException-
 * y siempre libera el candado de concurrencia en su {@code finally}.
 *
 * <p><b>Concurrencia:</b> {@code enEjecucion} (AtomicBoolean, CAS) es la
 * unica fuente de verdad dentro del proceso: una segunda llamada mientras
 * hay un respaldo en curso se rechaza ANTES de crear ninguna fila de
 * historial. {@code historialRepository.existsByEstado(EN_PROCESO)} es una
 * segunda guarda, mas lenta pero persistente, para el caso borde de un
 * reinicio del backend a mitad de un respaldo (el AtomicBoolean nace en
 * false en cada arranque; esa fila huerfana en EN_PROCESO se detecta y
 * bloquea nuevos intentos hasta que un operador la revise a mano).
 */
@Service
public class RespaldoEjecucionService {

    private static final Logger log = LoggerFactory.getLogger(RespaldoEjecucionService.class);
    // withZone(UTC) es imprescindible: un DateTimeFormatter de patron
    // (campos de calendario: ano/mes/dia) no puede formatear un Instant
    // "pelado" sin una zona -un Instant es solo epoch-segundos, sin campos
    // de calendario propios- y lanzaria UnsupportedTemporalTypeException en
    // cada respaldo.
    private static final DateTimeFormatter FORMATO_NOMBRE_ARCHIVO =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final RespaldoHistorialRepository historialRepository;
    private final RespaldoConfiguracionService configuracionService;
    private final PgDumpExecutor pgDumpExecutor;
    private final BackupProperties properties;
    private final AuditoriaService auditoriaService;
    private final Executor executor;
    private final Clock clock;

    private final AtomicBoolean enEjecucion = new AtomicBoolean(false);

    // @Autowired explicito: hay un segundo constructor (con Executor/Clock
    // de test) y sin esta anotacion Spring exige un constructor sin
    // argumentos en vez de elegir este entre los dos.
    @Autowired
    public RespaldoEjecucionService(RespaldoHistorialRepository historialRepository,
                                     RespaldoConfiguracionService configuracionService,
                                     PgDumpExecutor pgDumpExecutor,
                                     BackupProperties properties,
                                     AuditoriaService auditoriaService,
                                     @Qualifier("respaldoExecutor") Executor executor) {
        this(historialRepository, configuracionService, pgDumpExecutor, properties, auditoriaService, executor,
                Clock.systemUTC());
    }

    RespaldoEjecucionService(RespaldoHistorialRepository historialRepository,
                              RespaldoConfiguracionService configuracionService,
                              PgDumpExecutor pgDumpExecutor,
                              BackupProperties properties,
                              AuditoriaService auditoriaService,
                              Executor executor,
                              Clock clock) {
        this.historialRepository = historialRepository;
        this.configuracionService = configuracionService;
        this.pgDumpExecutor = pgDumpExecutor;
        this.properties = properties;
        this.auditoriaService = auditoriaService;
        this.executor = executor;
        this.clock = clock;
    }

    /** Disparado por un ROLE_ADMIN desde "Generar respaldo ahora". Rechaza con 409 si ya hay uno en curso. */
    public RespaldoHistorialResponse ejecutarManual(String emailAdmin) {
        if (!reservarEjecucion()) {
            throw new RespaldoEnProcesoException(
                    "Ya hay un respaldo en proceso. Espere a que finalice antes de generar otro.");
        }
        RespaldoHistorial historial = crearHistorialEnProceso(TipoRespaldo.MANUAL, emailAdmin);
        auditoriaService.registrar(emailAdmin, "BACKUP_MANUAL_REQUESTED", "admin/respaldos",
                "respaldo #" + historial.getId(), "POST", "SUCCESS", 202);
        executor.execute(() -> ejecutarEnSegundoPlano(historial.getId()));
        return toResponse(historial);
    }

    /**
     * Disparado por RespaldoScheduler cuando corresponde. A diferencia de
     * {@link #ejecutarManual}, si ya hay un respaldo en curso simplemente
     * no hace nada -el scheduler ya considera ese caso "no es mi turno
     * todavia", no un error que reportar.
     */
    public void intentarEjecutarAutomatico() {
        if (!reservarEjecucion()) {
            log.debug("Se omite el respaldo automatico de esta revision: ya hay uno en proceso.");
            return;
        }
        RespaldoHistorial historial = crearHistorialEnProceso(TipoRespaldo.AUTOMATICO, null);
        auditoriaService.registrar(null, "BACKUP_AUTOMATIC_STARTED", "admin/respaldos",
                "respaldo #" + historial.getId(), null, "SUCCESS", null);
        executor.execute(() -> ejecutarEnSegundoPlano(historial.getId()));
    }

    @Transactional(readOnly = true)
    public Page<RespaldoHistorialResponse> historial(Pageable pageable) {
        return historialRepository.findAllByOrderByIniciadoEnDesc(pageable).map(this::toResponse);
    }

    // ---------- Concurrencia ----------

    private boolean reservarEjecucion() {
        if (!enEjecucion.compareAndSet(false, true)) {
            return false;
        }
        // Segunda guarda (persistente, ver javadoc de la clase): si el CAS
        // en memoria dice "libre" pero la base tiene una fila huerfana en
        // EN_PROCESO de un reinicio anterior, no se dispara un respaldo
        // nuevo por encima de esa duda.
        if (historialRepository.existsByEstado(EstadoRespaldo.EN_PROCESO)) {
            enEjecucion.set(false);
            log.warn("Se encontro una fila respaldo_historial en EN_PROCESO de un arranque anterior; "
                    + "no se dispara un nuevo respaldo hasta que un operador la revise.");
            return false;
        }
        return true;
    }

    // ---------- Ejecucion en segundo plano ----------

    // crearHistorialEnProceso/finalizarHistorial se llaman siempre por
    // auto-invocacion (this.metodo(...) dentro de la misma instancia), asi
    // que un @Transactional propio aqui NO pasaria por el proxy de Spring y
    // seria enganoso. No hace falta: JpaRepository.save(...) ya abre su
    // propia transaccion, es la unica operacion de base de datos de cada
    // metodo.
    RespaldoHistorial crearHistorialEnProceso(TipoRespaldo tipo, String ejecutadoPor) {
        RespaldoHistorial historial = RespaldoHistorial.builder()
                .tipo(tipo)
                .estado(EstadoRespaldo.EN_PROCESO)
                .iniciadoEn(clock.instant())
                .ejecutadoPor(ejecutadoPor)
                .build();
        return historialRepository.save(historial);
    }

    /** Corre en {@code executor} -nunca en el hilo HTTP ni en el del scheduler. */
    private void ejecutarEnSegundoPlano(Long historialId) {
        try {
            Path archivo = construirRutaArchivo(historialId);
            Files.createDirectories(archivo.getParent());

            ConexionPostgres conexion = JdbcPostgresUrlParser.parse(properties.getPgdumpSourceUrl());
            pgDumpExecutor.ejecutar(new PgDumpParametros(
                    conexion.host(), conexion.port(), conexion.database(),
                    properties.getPgdumpUsuario(), properties.getPgdumpPassword(), archivo));

            long tamanoBytes = Files.size(archivo);
            finalizarHistorial(historialId, EstadoRespaldo.EXITOSO,
                    archivo.getFileName().toString(), tamanoBytes, null);
        } catch (PgDumpExecutionException e) {
            log.error("Respaldo #{} fallo en pg_dump (tipo={})", historialId, e.getTipo(), e);
            finalizarHistorial(historialId, EstadoRespaldo.FALLIDO, null, null, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Respaldo #{} fallo: configuracion de conexion invalida", historialId, e);
            finalizarHistorial(historialId, EstadoRespaldo.FALLIDO, null, null,
                    "No se pudo determinar la conexion de PostgreSQL para el respaldo.");
        } catch (IOException e) {
            log.error("Respaldo #{} fallo por un error de E/S al preparar el archivo de salida", historialId, e);
            finalizarHistorial(historialId, EstadoRespaldo.FALLIDO, null, null,
                    "No se pudo escribir el archivo de respaldo en el almacenamiento configurado.");
        } catch (Exception e) {
            // Red de seguridad final: NADA de lo que pase aqui debe escapar
            // de este hilo de fondo ni tumbar BIOPET.
            log.error("Respaldo #{} fallo por un error inesperado", historialId, e);
            finalizarHistorial(historialId, EstadoRespaldo.FALLIDO, null, null,
                    "Error inesperado al generar el respaldo.");
        } finally {
            Instant momento = clock.instant();
            try {
                configuracionService.marcarEjecutado(momento);
            } catch (Exception e) {
                // Si ni siquiera esto se puede guardar, se loguea y se sigue:
                // el proximo tick del scheduler ya recalculara con datos
                // frescos, y liberar enEjecucion (abajo) es lo que de verdad
                // importa para no dejar el modulo bloqueado.
                log.error("No se pudo actualizar respaldo_configuracion tras el respaldo #{}", historialId, e);
            }
            enEjecucion.set(false);
        }
    }

    void finalizarHistorial(Long historialId, EstadoRespaldo estado, String nombreArchivo,
                             Long tamanoBytes, String mensajeErrorSeguro) {
        RespaldoHistorial historial = historialRepository.findById(historialId).orElse(null);
        if (historial == null) {
            log.error("No se encontro la fila de historial #{} para finalizarla", historialId);
            return;
        }
        Instant finalizadoEn = clock.instant();
        historial.setEstado(estado);
        historial.setFinalizadoEn(finalizadoEn);
        historial.setDuracionMs(Duration.between(historial.getIniciadoEn(), finalizadoEn).toMillis());
        historial.setNombreArchivo(nombreArchivo);
        historial.setTamanoBytes(tamanoBytes);
        historial.setMensajeErrorSeguro(mensajeErrorSeguro);
        historialRepository.save(historial);

        boolean exitoso = estado == EstadoRespaldo.EXITOSO;
        auditoriaService.registrar(historial.getEjecutadoPor(), exitoso ? "BACKUP_COMPLETED" : "BACKUP_FAILED",
                "admin/respaldos", "respaldo #" + historialId, null,
                exitoso ? "SUCCESS" : "FAILURE", null);
    }

    private Path construirRutaArchivo(Long historialId) {
        String nombre = "biopet-respaldo-" + FORMATO_NOMBRE_ARCHIVO.format(clock.instant())
                + "-" + historialId + ".dump";
        return Path.of(properties.getStoragePath(), nombre);
    }

    private RespaldoHistorialResponse toResponse(RespaldoHistorial h) {
        return new RespaldoHistorialResponse(
                h.getId(), h.getTipo(), h.getEstado(), h.getIniciadoEn(), h.getFinalizadoEn(),
                h.getDuracionMs(), h.getNombreArchivo(), h.getTamanoBytes(),
                h.getMensajeErrorSeguro(), h.getEjecutadoPor()
        );
    }
}
