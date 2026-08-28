package com.biopet.facturacion.persistence;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.SecuencialEmision;
import com.biopet.facturacion.repository.EmisorFiscalRepository;
import com.biopet.facturacion.repository.PuntoEmisionRepository;
import com.biopet.facturacion.repository.SecuencialEmisionRepository;
import com.biopet.facturacion.service.SecuencialService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia real sobre PostgreSQL: varios hilos, cada uno con su PROPIA
 * transaccion, peleando por el mismo contador fiscal.
 *
 * <p>Dos detalles hacen que esta prueba signifique algo:
 *
 * <ul>
 *   <li>La clase NO es {@code @Transactional}. Si los 50 workers corriesen
 *       dentro de la transaccion del test compartirian la misma conexion y el
 *       mismo contexto de persistencia, no habria contencion ninguna y el test
 *       pasaria siempre sin probar nada. Cada worker abre su transaccion con
 *       {@link TransactionTemplate} en su propio hilo.</li>
 *   <li>Todos esperan en una barrera y arrancan a la vez. Sin eso, los hilos se
 *       irian escalonando de forma natural y la colision seria casual. No se
 *       usan sleeps: la sincronizacion es por latch.</li>
 * </ul>
 *
 * <p>Lo que se exige no es solo "50 valores distintos" (eso lo cumpliria
 * tambien una numeracion con huecos, que seria ilegal): se exige exactamente la
 * secuencia 1..50, contigua, y que el contador termine en 50.
 */
@SpringBootTest
class SecuencialConcurrenciaIntegrationTest extends FacturacionPostgresTestBase {

    private static final Logger log = LoggerFactory.getLogger(SecuencialConcurrenciaIntegrationTest.class);

    @Autowired SecuencialService secuencialService;
    @Autowired SecuencialEmisionRepository secuencialEmisionRepository;
    @Autowired EmisorFiscalRepository emisorFiscalRepository;
    @Autowired PuntoEmisionRepository puntoEmisionRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private static final AtomicInteger SECUENCIA_FIXTURE = new AtomicInteger(500);

    // ==================================================================
    // 50 hilos, mismo punto, mismo ambiente
    // ==================================================================

    @Test
    void cincuentaTransaccionesConcurrentesReservanLaSerie1a50SinDuplicadosNiHuecos() throws Exception {
        PuntoEmision punto = nuevoPunto();
        crearContador(punto, AmbienteSri.PRUEBAS, 0L);

        ResultadoCarrera carrera = ejecutarEnParalelo(50,
                () -> () -> secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS));

        log.info("50 reservas concurrentes sobre un unico contador: {} ms", carrera.duracion().toMillis());

        assertThat(carrera.errores()).as("ningun worker debe fallar").isEmpty();
        assertThat(carrera.numeros()).hasSize(50);
        assertThat(carrera.numeros()).doesNotHaveDuplicates();
        // Contiguo de 1 a 50: ni saltos ni repeticiones.
        assertThat(carrera.numeros().stream().sorted().toList())
                .isEqualTo(LongStream.rangeClosed(1, 50).boxed().toList());
        assertThat(carrera.numeros().stream().mapToLong(Long::longValue).min().orElseThrow())
                .isEqualTo(1L);
        assertThat(carrera.numeros().stream().mapToLong(Long::longValue).max().orElseThrow())
                .isEqualTo(50L);

        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(50L);
    }

    // ==================================================================
    // 25 + 25 sobre el MISMO punto, ambientes distintos
    // ==================================================================

    @Test
    void pruebasYProduccionAvanzanEnParaleloSobreElMismoPuntoSinInterferirse() throws Exception {
        PuntoEmision punto = nuevoPunto();
        crearContador(punto, AmbienteSri.PRUEBAS, 0L);
        crearContador(punto, AmbienteSri.PRODUCCION, 0L);

        List<Long> pruebas = new ArrayList<>();
        List<Long> produccion = new ArrayList<>();

        ResultadoCarrera carrera = ejecutarEnParalelo(50, indice -> () -> {
            AmbienteSri ambiente = indice % 2 == 0 ? AmbienteSri.PRUEBAS : AmbienteSri.PRODUCCION;
            long numero = secuencialService.reservar(punto.getId(), ambiente);
            synchronized (pruebas) {
                (ambiente == AmbienteSri.PRUEBAS ? pruebas : produccion).add(numero);
            }
            return numero;
        });

        log.info("25 + 25 reservas concurrentes en dos ambientes del mismo punto: {} ms",
                carrera.duracion().toMillis());

        assertThat(carrera.errores()).isEmpty();

        // Cada ambiente lleva su propia serie completa 1..25.
        assertThat(pruebas.stream().sorted().toList())
                .isEqualTo(LongStream.rangeClosed(1, 25).boxed().toList());
        assertThat(produccion.stream().sorted().toList())
                .isEqualTo(LongStream.rangeClosed(1, 25).boxed().toList());

        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(25L);
        assertThat(ultimoEnBd(punto, AmbienteSri.PRODUCCION)).isEqualTo(25L);
    }

    // ==================================================================
    // 25 + 25 sobre DOS puntos distintos, mismo ambiente
    // ==================================================================

    @Test
    void dosPuntosDeEmisionAvanzanEnParaleloSinSerializarseEntreSi() throws Exception {
        EmisorFiscal emisor = nuevoEmisor();
        PuntoEmision uno = nuevoPunto(emisor, "001");
        PuntoEmision dos = nuevoPunto(emisor, "002");
        crearContador(uno, AmbienteSri.PRUEBAS, 0L);
        crearContador(dos, AmbienteSri.PRUEBAS, 0L);

        List<Long> deUno = new ArrayList<>();
        List<Long> deDos = new ArrayList<>();

        ResultadoCarrera carrera = ejecutarEnParalelo(50, indice -> () -> {
            PuntoEmision punto = indice % 2 == 0 ? uno : dos;
            long numero = secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS);
            synchronized (deUno) {
                (punto == uno ? deUno : deDos).add(numero);
            }
            return numero;
        });

        log.info("25 + 25 reservas concurrentes sobre dos puntos de emision: {} ms",
                carrera.duracion().toMillis());

        assertThat(carrera.errores()).isEmpty();
        assertThat(deUno.stream().sorted().toList())
                .isEqualTo(LongStream.rangeClosed(1, 25).boxed().toList());
        assertThat(deDos.stream().sorted().toList())
                .isEqualTo(LongStream.rangeClosed(1, 25).boxed().toList());

        assertThat(ultimoEnBd(uno, AmbienteSri.PRUEBAS)).isEqualTo(25L);
        assertThat(ultimoEnBd(dos, AmbienteSri.PRUEBAS)).isEqualTo(25L);
    }

    // ==================================================================
    // Motor de la carrera
    // ==================================================================

    private record ResultadoCarrera(List<Long> numeros, List<Throwable> errores, Duration duracion) {
    }

    private ResultadoCarrera ejecutarEnParalelo(int workers, Supplier<Callable<Long>> tarea) throws Exception {
        return ejecutarEnParalelo(workers, indice -> tarea.get());
    }

    /**
     * Lanza {@code workers} hilos que esperan en una barrera y arrancan a la
     * vez. Cada uno ejecuta su tarea dentro de su PROPIA transaccion.
     */
    private ResultadoCarrera ejecutarEnParalelo(int workers,
                                                java.util.function.IntFunction<Callable<Long>> tareaPorIndice)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch listos = new CountDownLatch(workers);
        CountDownLatch arranque = new CountDownLatch(1);
        List<Future<Long>> futuros = new ArrayList<>(workers);

        try {
            for (int i = 0; i < workers; i++) {
                int indice = i;
                futuros.add(executor.submit(() -> {
                    Callable<Long> tarea = tareaPorIndice.apply(indice);
                    listos.countDown();
                    arranque.await(30, TimeUnit.SECONDS);
                    // Transaccion propia del worker: es lo que convierte esto
                    // en concurrencia real contra PostgreSQL.
                    return new TransactionTemplate(transactionManager).execute(status -> {
                        try {
                            return tarea.call();
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    });
                }));
            }

            assertThat(listos.await(30, TimeUnit.SECONDS)).as("todos los workers listos").isTrue();

            long inicio = System.nanoTime();
            arranque.countDown();

            List<Long> numeros = new ArrayList<>();
            List<Throwable> errores = new ArrayList<>();
            for (Future<Long> futuro : futuros) {
                try {
                    numeros.add(futuro.get(60, TimeUnit.SECONDS));
                } catch (java.util.concurrent.ExecutionException e) {
                    errores.add(e.getCause());
                }
            }
            Duration duracion = Duration.ofNanos(System.nanoTime() - inicio);

            return new ResultadoCarrera(numeros, errores, duracion);
        } finally {
            arranque.countDown();
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    // ==================================================================
    // Fixtures ficticios
    // ==================================================================

    private int siguiente() {
        return SECUENCIA_FIXTURE.incrementAndGet();
    }

    private EmisorFiscal nuevoEmisor() {
        return emisorFiscalRepository.save(EmisorFiscal.builder()
                .ruc(String.valueOf(7_000_000_000_000L + siguiente()))
                .razonSocial("EMISOR FICTICIO CONCURRENCIA")
                .direccionMatriz("Direccion ficticia")
                .obligadoContabilidad(false)
                .rimpe(false)
                .activo(true)
                .build());
    }

    private PuntoEmision nuevoPunto() {
        return nuevoPunto(nuevoEmisor(), "001");
    }

    private PuntoEmision nuevoPunto(EmisorFiscal emisor, String puntoEmision) {
        return puntoEmisionRepository.save(PuntoEmision.builder()
                .emisorFiscal(emisor)
                .establecimiento(String.format("%03d", siguiente() % 1000))
                .puntoEmision(puntoEmision)
                .activo(true)
                .build());
    }

    private SecuencialEmision crearContador(PuntoEmision punto, AmbienteSri ambiente, long ultimo) {
        return secuencialEmisionRepository.save(SecuencialEmision.builder()
                .puntoEmision(punto)
                .ambiente(ambiente)
                .ultimoSecuencial(ultimo)
                .build());
    }

    private long ultimoEnBd(PuntoEmision punto, AmbienteSri ambiente) {
        return jdbc.queryForObject(
                "SELECT ultimo_secuencial FROM secuencial_emision "
                        + "WHERE punto_emision_id = ? AND ambiente = ?",
                Long.class, punto.getId(), Short.valueOf(ambiente.codigo()));
    }
}
