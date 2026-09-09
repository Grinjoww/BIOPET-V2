package com.biopet.backup.service;

import com.biopet.audit.service.AuditoriaService;
import com.biopet.backup.config.BackupProperties;
import com.biopet.backup.dto.RespaldoHistorialResponse;
import com.biopet.backup.entity.EstadoRespaldo;
import com.biopet.backup.entity.RespaldoHistorial;
import com.biopet.backup.entity.TipoRespaldo;
import com.biopet.backup.exception.RespaldoEnProcesoException;
import com.biopet.backup.pgdump.PgDumpExecutionException;
import com.biopet.backup.pgdump.PgDumpExecutor;
import com.biopet.backup.pgdump.TipoFalloPgDump;
import com.biopet.backup.repository.RespaldoHistorialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.PageImpl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "En tests mockear el ejecutor de pg_dump": ningun test de esta clase
 * lanza un proceso pg_dump real -PgDumpExecutor siempre es un mock (o un
 * fake en memoria) inyectado directamente en el constructor de test del
 * servicio.
 *
 * <p>El repositorio de historial tambien se sustituye por un fake en
 * memoria (un Map respaldado por los mismos metodos que usa el servicio)
 * en lugar de puro Mockito, porque el flujo real necesita que
 * {@code save} devuelva la misma fila que luego {@code findById} recupera
 * -imprescindible para probar de verdad el ciclo EN_PROCESO -> EXITOSO/
 * FALLIDO sin una base de datos.
 */
class RespaldoEjecucionServiceTest {

    private final Map<Long, RespaldoHistorial> filas = new ConcurrentHashMap<>();
    private final AtomicLong secuenciaId = new AtomicLong(0);

    @TempDir
    Path directorioRespaldos;

    private RespaldoHistorialRepository historialRepository;
    private RespaldoConfiguracionService configuracionService;
    private BackupProperties properties;
    private AuditoriaService auditoriaService;

    @BeforeEach
    void setUp() {
        filas.clear();
        historialRepository = mock(RespaldoHistorialRepository.class);

        when(historialRepository.save(any())).thenAnswer(inv -> {
            RespaldoHistorial h = inv.getArgument(0);
            if (h.getId() == null) {
                h.setId(secuenciaId.incrementAndGet());
            }
            filas.put(h.getId(), h);
            return h;
        });
        when(historialRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(filas.get((Long) inv.getArgument(0))));
        when(historialRepository.existsByEstado(any())).thenAnswer(inv -> {
            EstadoRespaldo estado = inv.getArgument(0);
            return filas.values().stream().anyMatch(h -> h.getEstado() == estado);
        });
        when(historialRepository.findAllByOrderByIniciadoEnDesc(any())).thenAnswer(inv -> {
            List<RespaldoHistorial> ordenadas = filas.values().stream()
                    .sorted((a, b) -> b.getIniciadoEn().compareTo(a.getIniciadoEn()))
                    .toList();
            return new PageImpl<>(ordenadas);
        });

        configuracionService = mock(RespaldoConfiguracionService.class);
        auditoriaService = mock(AuditoriaService.class);

        properties = new BackupProperties();
        properties.setStoragePath(directorioRespaldos.toString());
        properties.setPgdumpSourceUrl("jdbc:postgresql://localhost:5432/biopet_test");
        properties.setPgdumpUsuario("biopet_user");
        properties.setPgdumpPassword("no-se-usa-porque-el-executor-esta-mockeado");
    }

    // ---------- Manual: genera historial y termina EXITOSO ----------

    @Test
    void manualCreaFilaEnProcesoInmediatamenteYLuegoQuedaExitosa() {
        PgDumpExecutor executorQueEscribeUnArchivo = params -> {
            try {
                Files.writeString(params.rutaSalida(), "contenido de prueba, no es un dump real");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        RespaldoEjecucionService service = new RespaldoEjecucionService(
                historialRepository, configuracionService, executorQueEscribeUnArchivo, properties, auditoriaService,
                Runnable::run, Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC));

        RespaldoHistorialResponse respuesta = service.ejecutarManual("admin@biopet.ec");

        // Con executor "Runnable::run" (sincrono) el trabajo de fondo ya
        // termino cuando ejecutarManual retorna: se puede leer el resultado
        // final directamente, sin sondear.
        RespaldoHistorial finalRow = filas.get(respuesta.id());
        assertThat(finalRow.getEstado()).isEqualTo(EstadoRespaldo.EXITOSO);
        assertThat(finalRow.getTipo()).isEqualTo(TipoRespaldo.MANUAL);
        assertThat(finalRow.getEjecutadoPor()).isEqualTo("admin@biopet.ec");
        assertThat(finalRow.getNombreArchivo()).isNotBlank();
        assertThat(finalRow.getTamanoBytes()).isGreaterThan(0);
        assertThat(finalRow.getDuracionMs()).isGreaterThanOrEqualTo(0);

        verify(configuracionService).marcarEjecutado(any());
    }

    // ---------- Fallo simulado de pg_dump => FALLIDO ----------

    @Test
    void fallosDePgDumpQuedanRegistradosComoFallido() {
        PgDumpExecutor executorQueFalla = params -> {
            throw new PgDumpExecutionException(TipoFalloPgDump.CODIGO_SALIDA, "pg_dump finalizo con codigo 1.");
        };
        RespaldoEjecucionService service = new RespaldoEjecucionService(
                historialRepository, configuracionService, executorQueFalla, properties, auditoriaService,
                Runnable::run, Clock.systemUTC());

        RespaldoHistorialResponse respuesta = service.ejecutarManual("admin@biopet.ec");

        RespaldoHistorial finalRow = filas.get(respuesta.id());
        assertThat(finalRow.getEstado()).isEqualTo(EstadoRespaldo.FALLIDO);
        assertThat(finalRow.getMensajeErrorSeguro()).isEqualTo("pg_dump finalizo con codigo 1.");
        assertThat(finalRow.getNombreArchivo()).isNull();
        assertThat(finalRow.getTamanoBytes()).isNull();

        // El fallo de un respaldo no debe impedir que se reprograme el proximo.
        verify(configuracionService).marcarEjecutado(any());
    }

    @Test
    void unErrorInesperadoDelExecutorTambienQuedaComoFallidoSinPropagar() {
        PgDumpExecutor executorQueExplotaFeo = params -> {
            throw new IllegalStateException("boom, algo que ningun catch especifico anticipo");
        };
        RespaldoEjecucionService service = new RespaldoEjecucionService(
                historialRepository, configuracionService, executorQueExplotaFeo, properties, auditoriaService,
                Runnable::run, Clock.systemUTC());

        // No debe propagar la excepcion fuera del metodo publico.
        RespaldoHistorialResponse respuesta = service.ejecutarManual("admin@biopet.ec");

        RespaldoHistorial finalRow = filas.get(respuesta.id());
        assertThat(finalRow.getEstado()).isEqualTo(EstadoRespaldo.FALLIDO);
        assertThat(finalRow.getMensajeErrorSeguro()).doesNotContain("boom");
    }

    @Test
    void unaUrlDeConexionInvalidaQuedaComoFallidoSinPropagar() {
        properties.setPgdumpSourceUrl("no-es-una-url-jdbc-valida");
        PgDumpExecutor executorQueNuncaDeberiaLlamarse = mock(PgDumpExecutor.class);
        RespaldoEjecucionService service = new RespaldoEjecucionService(
                historialRepository, configuracionService, executorQueNuncaDeberiaLlamarse, properties, auditoriaService,
                Runnable::run, Clock.systemUTC());

        RespaldoHistorialResponse respuesta = service.ejecutarManual("admin@biopet.ec");

        RespaldoHistorial finalRow = filas.get(respuesta.id());
        assertThat(finalRow.getEstado()).isEqualTo(EstadoRespaldo.FALLIDO);
        verify(executorQueNuncaDeberiaLlamarse, never()).ejecutar(any());
    }

    // ---------- Automatico: no lanza excepcion si ya hay uno en curso ----------

    @Test
    void automaticoNoLanzaExcepcionSiYaHayUnoEnProcesoSoloLoOmite() throws Exception {
        CountDownLatch bloqueo = new CountDownLatch(1);
        CountDownLatch dumpIniciado = new CountDownLatch(1);
        PgDumpExecutor executorLento = params -> {
            dumpIniciado.countDown();
            esperar(bloqueo);
        };
        RespaldoEjecucionService service = new RespaldoEjecucionService(
                historialRepository, configuracionService, executorLento, properties, auditoriaService,
                Executors.newSingleThreadExecutor(), Clock.systemUTC());

        service.ejecutarManual("admin@biopet.ec");
        assertThat(dumpIniciado.await(2, TimeUnit.SECONDS)).as("el respaldo manual debio empezar a correr").isTrue();

        service.intentarEjecutarAutomatico(); // no debe lanzar, solo omitirse

        assertThat(filas.values()).hasSize(1); // no se creo una segunda fila de historial
        bloqueo.countDown();
        esperarHasta(() -> filas.values().stream().noneMatch(h -> h.getEstado() == EstadoRespaldo.EN_PROCESO));
    }

    // ---------- Concurrencia bloqueada ----------

    @Test
    void segundoRespaldoManualEsRechazadoMientrasElPrimeroEstaEnProceso() throws Exception {
        CountDownLatch bloqueoPgDump = new CountDownLatch(1);
        CountDownLatch dumpIniciado = new CountDownLatch(1);
        PgDumpExecutor executorLento = params -> {
            dumpIniciado.countDown();
            esperar(bloqueoPgDump);
        };
        RespaldoEjecucionService service = new RespaldoEjecucionService(
                historialRepository, configuracionService, executorLento, properties, auditoriaService,
                Executors.newSingleThreadExecutor(), Clock.systemUTC());

        service.ejecutarManual("admin@biopet.ec");
        assertThat(dumpIniciado.await(2, TimeUnit.SECONDS)).as("el primer respaldo debio empezar a correr").isTrue();

        assertThatThrownBy(() -> service.ejecutarManual("otro-admin@biopet.ec"))
                .isInstanceOf(RespaldoEnProcesoException.class);

        bloqueoPgDump.countDown();
        esperarHasta(() -> filas.values().stream().noneMatch(h -> h.getEstado() == EstadoRespaldo.EN_PROCESO));

        // Tras liberar el primero, un tercer intento SI debe poder correr.
        RespaldoHistorialResponse tercero = service.ejecutarManual("otro-admin@biopet.ec");
        assertThat(tercero).isNotNull();
    }

    @Test
    void filaHuerfanaEnProcesoDeUnReinicioAnteriorBloqueaNuevosIntentos() {
        RespaldoHistorial huerfana = RespaldoHistorial.builder()
                .id(secuenciaId.incrementAndGet())
                .tipo(TipoRespaldo.AUTOMATICO)
                .estado(EstadoRespaldo.EN_PROCESO)
                .iniciadoEn(Instant.now().minusSeconds(3600))
                .build();
        filas.put(huerfana.getId(), huerfana);

        RespaldoEjecucionService service = new RespaldoEjecucionService(
                historialRepository, configuracionService, mock(PgDumpExecutor.class), properties, auditoriaService,
                Runnable::run, Clock.systemUTC());

        assertThatThrownBy(() -> service.ejecutarManual("admin@biopet.ec"))
                .isInstanceOf(RespaldoEnProcesoException.class);
    }

    // ---------- Helpers ----------

    private void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("El test no libero el latch a tiempo");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Sondeo acotado (sin Awaitility: no es una dependencia de este proyecto), nunca un sleep fijo a ciegas. */
    private void esperarHasta(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 2000;
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() > limite) {
                throw new AssertionError("La condicion esperada no se cumplio a tiempo");
            }
            Thread.sleep(20);
        }
    }
}
