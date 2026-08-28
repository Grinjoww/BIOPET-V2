package com.biopet.facturacion.persistence;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.entity.SecuencialEmision;
import com.biopet.facturacion.exception.SecuencialAgotadoException;
import com.biopet.facturacion.exception.SecuencialNoConfiguradoException;
import com.biopet.facturacion.repository.EmisorFiscalRepository;
import com.biopet.facturacion.repository.FacturaRepository;
import com.biopet.facturacion.repository.PuntoEmisionRepository;
import com.biopet.facturacion.repository.SecuencialEmisionRepository;
import com.biopet.facturacion.service.SecuencialService;
import com.biopet.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Semantica de {@link SecuencialService} sobre PostgreSQL real: validacion,
 * errores de configuracion, tope de la serie, independencia entre ambientes y
 * puntos, y -lo mas importante- el comportamiento transaccional.
 *
 * <p>La clase NO es {@code @Transactional}, a diferencia de las pruebas de
 * persistencia de la Fase 4A. Es imprescindible: si el test envolviese todo en
 * una transaccion que al final se deshace, no se podria distinguir un commit de
 * un rollback, que es justo lo que hay que demostrar. Cada bloque transaccional
 * se abre aqui de forma explicita con {@link TransactionTemplate}.
 *
 * <p>La concurrencia real (50 hilos, ambientes y puntos en paralelo) vive en
 * {@link SecuencialConcurrenciaIntegrationTest}.
 */
@SpringBootTest
class SecuencialServiceIntegrationTest extends FacturacionPostgresTestBase {

    @Autowired SecuencialService secuencialService;
    @Autowired SecuencialEmisionRepository secuencialEmisionRepository;
    @Autowired EmisorFiscalRepository emisorFiscalRepository;
    @Autowired PuntoEmisionRepository puntoEmisionRepository;
    @Autowired FacturaRepository facturaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    /**
     * La base se comparte entre todas las clases del modulo y estos tests SI
     * confirman sus datos, asi que cada fixture necesita valores propios.
     */
    private static final AtomicInteger SECUENCIA_FIXTURE = new AtomicInteger();

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    // ==================================================================
    // Validacion de entrada
    // ==================================================================

    @Test
    void argumentosNulosFallanDeInmediatoYNoLleganAConsultaAlguna() {
        assertThatThrownBy(() -> secuencialService.reservar(null, AmbienteSri.PRUEBAS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("punto de emision");

        PuntoEmision punto = nuevoPunto();
        assertThatThrownBy(() -> secuencialService.reservar(punto.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ambiente");
    }

    // ==================================================================
    // Configuracion ausente
    // ==================================================================

    @Test
    void sinContadorConfiguradoFallaSinCrearloNiUsarElOtroAmbiente() {
        PuntoEmision punto = nuevoPunto();
        // Solo se configura PRUEBAS. PRODUCCION queda deliberadamente sin fila.
        crearContador(punto, AmbienteSri.PRUEBAS, 0L);

        assertThatThrownBy(() -> secuencialService.reservar(punto.getId(), AmbienteSri.PRODUCCION))
                .isInstanceOf(SecuencialNoConfiguradoException.class)
                .hasMessageContaining("PRODUCCION")
                .hasMessageContaining(String.valueOf(punto.getId()))
                // La excepcion lleva los datos estructurados, no solo el texto:
                // la futura capa REST los necesitara para componer el
                // ProblemDetail sin tener que parsear el mensaje.
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(SecuencialNoConfiguradoException.class))
                .satisfies(e -> {
                    assertThat(e.getPuntoEmisionId()).isEqualTo(punto.getId());
                    assertThat(e.getAmbiente()).isEqualTo(AmbienteSri.PRODUCCION);
                });

        // No se creo ninguna fila al vuelo...
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM secuencial_emision WHERE punto_emision_id = ?",
                Integer.class, punto.getId())).isEqualTo(1);
        assertThat(secuencialEmisionRepository
                .findByPuntoEmision_IdAndAmbiente(punto.getId(), AmbienteSri.PRODUCCION))
                .isEmpty();
        // ...y el contador del OTRO ambiente no se toco ni se uso como sustituto.
        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isZero();
    }

    @Test
    void unPuntoDeEmisionInexistenteSeTrataComoConfiguracionAusente() {
        // Un punto que no existe y un punto sin contador son el mismo problema
        // operativo: no se puede numerar. Se resuelven igual (configurando la
        // serie), asi que no merecen dos excepciones ni dos consultas.
        assertThatThrownBy(() -> secuencialService.reservar(987_654_321L, AmbienteSri.PRUEBAS))
                .isInstanceOf(SecuencialNoConfiguradoException.class);
    }

    // ==================================================================
    // Commit y rollback
    // ==================================================================

    @Test
    void alConfirmarLaTransaccionElContadorAvanzaYElSiguienteNumeroContinua() {
        PuntoEmision punto = nuevoPunto();
        crearContador(punto, AmbienteSri.PRUEBAS, 10L);

        long reservado = tx().execute(status -> secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS));

        assertThat(reservado).isEqualTo(11L);
        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(11L);

        long siguiente = tx().execute(status -> secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS));
        assertThat(siguiente).isEqualTo(12L);
        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(12L);
    }

    @Test
    void alDeshacerLaTransaccionElNumeroNoSeConsume() {
        PuntoEmision punto = nuevoPunto();
        crearContador(punto, AmbienteSri.PRUEBAS, 10L);

        assertThatThrownBy(() -> tx().execute(status -> {
            long reservado = secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS);
            assertThat(reservado).isEqualTo(11L);
            // Algo falla despues de reservar y antes del commit.
            throw new IllegalStateException("fallo simulado despues de reservar");
        })).isInstanceOf(IllegalStateException.class);

        // El contador volvio a 10: el 11 NO quedo consumido.
        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(10L);

        // Y la siguiente reserva valida vuelve a entregar el 11, sin hueco.
        long siguiente = tx().execute(status -> secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS));
        assertThat(siguiente).isEqualTo(11L);
        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(11L);
    }

    @Test
    void elServicioSeUneALaTransaccionDeQuienLlamaYNoAbreUnaPropia() {
        // Esta es la prueba de que la propagacion NO es REQUIRES_NEW. Con
        // REQUIRES_NEW el incremento se confirmaria en su propia transaccion y
        // sobreviviria al rollback de fuera; el contador se quedaria en 6 y el 6
        // seria un numero consumido sin comprobante. Se comprueba el
        // comportamiento, no la anotacion.
        PuntoEmision punto = nuevoPunto();
        crearContador(punto, AmbienteSri.PRUEBAS, 5L);

        assertThatThrownBy(() -> tx().execute(status -> {
            secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS);
            secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS);
            throw new IllegalStateException("rollback de la transaccion externa");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(5L);
    }

    @Test
    void dosReservasEnLaMismaTransaccionEntreganNumerosConsecutivos() {
        PuntoEmision punto = nuevoPunto();
        crearContador(punto, AmbienteSri.PRUEBAS, 0L);

        List<Long> numeros = tx().execute(status -> List.of(
                secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS),
                secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS),
                secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS)));

        assertThat(numeros).containsExactly(1L, 2L, 3L);
        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(3L);
    }

    // ==================================================================
    // Ambientes y puntos independientes
    // ==================================================================

    @Test
    void pruebasYProduccionDelMismoPuntoLlevanSeriesIndependientes() {
        PuntoEmision punto = nuevoPunto();
        crearContador(punto, AmbienteSri.PRUEBAS, 0L);
        crearContador(punto, AmbienteSri.PRODUCCION, 0L);

        List<Long> pruebas = tx().execute(status -> List.of(
                secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS),
                secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS),
                secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS)));
        List<Long> produccion = tx().execute(status -> List.of(
                secuencialService.reservar(punto.getId(), AmbienteSri.PRODUCCION),
                secuencialService.reservar(punto.getId(), AmbienteSri.PRODUCCION)));

        // La misma serie 001-00X arranca en 1 en cada ambiente.
        assertThat(pruebas).containsExactly(1L, 2L, 3L);
        assertThat(produccion).containsExactly(1L, 2L);
        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(3L);
        assertThat(ultimoEnBd(punto, AmbienteSri.PRODUCCION)).isEqualTo(2L);
    }

    @Test
    void dosPuntosDeEmisionDelMismoEmisorLlevanSeriesIndependientes() {
        EmisorFiscal emisor = nuevoEmisor();
        PuntoEmision uno = nuevoPunto(emisor, "001");
        PuntoEmision dos = nuevoPunto(emisor, "002");
        crearContador(uno, AmbienteSri.PRUEBAS, 0L);
        crearContador(dos, AmbienteSri.PRUEBAS, 0L);

        tx().execute(status -> secuencialService.reservar(uno.getId(), AmbienteSri.PRUEBAS));
        tx().execute(status -> secuencialService.reservar(uno.getId(), AmbienteSri.PRUEBAS));
        long primeroDelDos = tx().execute(status -> secuencialService.reservar(dos.getId(), AmbienteSri.PRUEBAS));

        assertThat(primeroDelDos).isEqualTo(1L);
        assertThat(ultimoEnBd(uno, AmbienteSri.PRUEBAS)).isEqualTo(2L);
        assertThat(ultimoEnBd(dos, AmbienteSri.PRUEBAS)).isEqualTo(1L);
    }

    // ==================================================================
    // Tope de la serie
    // ==================================================================

    @Test
    void elUltimoNumeroSeEntregaYElSiguienteIntentoFallaSinMoverElContador() {
        PuntoEmision punto = nuevoPunto();
        crearContador(punto, AmbienteSri.PRUEBAS, 999_999_998L);

        long ultimo = tx().execute(status -> secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS));
        assertThat(ultimo).isEqualTo(999_999_999L);
        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(999_999_999L);

        assertThatThrownBy(() -> tx().execute(status ->
                secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(SecuencialAgotadoException.class)
                .hasMessageContaining("999999999")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(SecuencialAgotadoException.class))
                .satisfies(e -> {
                    assertThat(e.getPuntoEmisionId()).isEqualTo(punto.getId());
                    assertThat(e.getAmbiente()).isEqualTo(AmbienteSri.PRUEBAS);
                });

        // No hay overflow, no vuelve a 1, no salta de punto: se queda quieto.
        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(999_999_999L);
        assertThat(SecuencialService.SECUENCIAL_MAXIMO).isEqualTo(999_999_999L);
    }

    // ==================================================================
    // Atomicidad contador + factura (infraestructura para la fase futura)
    // ==================================================================

    @Test
    void siLaFacturaNoSeConfirmaElSecuencialTampocoSeConsume() {
        PuntoEmision punto = nuevoPunto();
        crearContador(punto, AmbienteSri.PRUEBAS, 0L);
        Usuario usuario = nuevoUsuario();

        // --- Intento que falla despues de numerar y persistir ---
        assertThatThrownBy(() -> tx().execute(status -> {
            long secuencial = secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS);
            facturaRepository.saveAndFlush(facturaEmitida(usuario, punto, secuencial));
            throw new IllegalStateException("fallo simulado antes del commit");
        })).isInstanceOf(IllegalStateException.class);

        // Ni factura ni numero consumido: o las dos cosas, o ninguna.
        assertThat(facturasDe(punto)).isZero();
        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isZero();

        // --- El mismo flujo, ahora confirmando ---
        long secuencial = tx().execute(status -> {
            long numero = secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS);
            facturaRepository.saveAndFlush(facturaEmitida(usuario, punto, numero));
            return numero;
        });

        assertThat(secuencial).isEqualTo(1L);
        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(1L);
        assertThat(facturasDe(punto)).isEqualTo(1);
    }

    // ==================================================================
    // Evidencia del bloqueo
    // ==================================================================

    @Test
    void mientrasUnaTransaccionReservaLaFilaQuedaBloqueadaSoloEsaFila() throws Exception {
        PuntoEmision punto = nuevoPunto();
        SecuencialEmision enPruebas = crearContador(punto, AmbienteSri.PRUEBAS, 0L);
        SecuencialEmision enProduccion = crearContador(punto, AmbienteSri.PRODUCCION, 0L);

        CountDownLatch bloqueoTomado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Long> reserva = executor.submit(() -> tx().execute(status -> {
                long numero = secuencialService.reservar(punto.getId(), AmbienteSri.PRUEBAS);
                bloqueoTomado.countDown();
                try {
                    // Se mantiene la transaccion abierta -y por tanto el
                    // bloqueo- mientras el hilo principal comprueba.
                    liberar.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return numero;
            }));

            assertThat(bloqueoTomado.await(15, TimeUnit.SECONDS)).isTrue();

            // 1. Desde OTRA conexion, pedir la misma fila con NOWAIT falla:
            //    prueba directa de que hay un FOR UPDATE vivo sobre ella.
            assertThatThrownBy(() -> seleccionarParaActualizarSinEsperar(enPruebas.getId()))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("could not obtain lock");

            // 2. La fila del OTRO ambiente, del MISMO punto, no esta bloqueada.
            //    Es lo que permite que PRUEBAS y PRODUCCION avancen a la vez.
            seleccionarParaActualizarSinEsperar(enProduccion.getId());

            // 3. Granularidad: el bloqueo alcanza a secuencial_emision y NO a
            //    punto_emision. Si la consulta del repository generase un JOIN,
            //    PostgreSQL habria tomado tambien un RowShareLock sobre
            //    punto_emision y todos los puntos de la clinica se
            //    serializarian entre si.
            List<String> tablasBloqueadas = jdbc.queryForList(
                    "SELECT DISTINCT c.relname FROM pg_locks l "
                            + "JOIN pg_class c ON c.oid = l.relation "
                            + "WHERE l.mode IN ('RowShareLock', 'RowExclusiveLock', "
                            + "'ShareUpdateExclusiveLock', 'ExclusiveLock', 'AccessExclusiveLock') "
                            + "AND c.relname IN ('secuencial_emision', 'punto_emision', 'emisor_fiscal')",
                    String.class);
            assertThat(tablasBloqueadas)
                    .contains("secuencial_emision")
                    .doesNotContain("punto_emision", "emisor_fiscal");

            liberar.countDown();
            assertThat(reserva.get(15, TimeUnit.SECONDS)).isEqualTo(1L);
        } finally {
            liberar.countDown();
            executor.shutdownNow();
        }

        assertThat(ultimoEnBd(punto, AmbienteSri.PRUEBAS)).isEqualTo(1L);
        assertThat(ultimoEnBd(punto, AmbienteSri.PRODUCCION)).isZero();
    }

    /** {@code FOR UPDATE NOWAIT}: falla en el acto si la fila ya esta bloqueada. */
    private void seleccionarParaActualizarSinEsperar(Long secuencialEmisionId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1 FROM secuencial_emision WHERE id = "
                    + secuencialEmisionId + " FOR UPDATE NOWAIT");
        }
    }

    // ==================================================================
    // Fixtures (todos ficticios)
    // ==================================================================

    private int siguiente() {
        return SECUENCIA_FIXTURE.incrementAndGet();
    }

    private EmisorFiscal nuevoEmisor() {
        return emisorFiscalRepository.save(EmisorFiscal.builder()
                .ruc(String.valueOf(8_000_000_000_000L + siguiente()))
                .razonSocial("EMISOR FICTICIO SECUENCIAL")
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

    private Usuario nuevoUsuario() {
        return usuarioRepository.save(Usuario.builder()
                .nombre("Dueno Secuencial")
                .email("secuencial-" + siguiente() + "@biopet.test")
                .passwordHash("x")
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build());
    }

    /**
     * Factura EMITIDA minima que satisface las constraints de V8. Datos
     * ficticios: la clave de acceso son 49 digitos bien formados pero no se
     * calcula con {@code ClaveAccesoGenerator}; esta fase no emite comprobantes,
     * solo demuestra que el contador y la factura comparten transaccion.
     */
    private Factura facturaEmitida(Usuario usuario, PuntoEmision punto, long secuencial) {
        return Factura.builder()
                .usuario(usuario)
                .puntoEmision(punto)
                .fechaEmision(LocalDate.of(2026, 9, 1))
                .estado(EstadoFactura.EMITIDA)
                .ambiente(AmbienteSri.PRUEBAS)
                .establecimiento(punto.getEstablecimiento())
                .puntoEmisionCodigo(punto.getPuntoEmision())
                .secuencial(secuencial)
                .claveAcceso(String.format("%049d", punto.getId() * 1_000_000L + secuencial))
                .build();
    }

    private long ultimoEnBd(PuntoEmision punto, AmbienteSri ambiente) {
        return jdbc.queryForObject(
                "SELECT ultimo_secuencial FROM secuencial_emision "
                        + "WHERE punto_emision_id = ? AND ambiente = ?",
                Long.class, punto.getId(), Short.valueOf(ambiente.codigo()));
    }

    private int facturasDe(PuntoEmision punto) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM facturas WHERE punto_emision_id = ?", Integer.class, punto.getId());
    }
}
