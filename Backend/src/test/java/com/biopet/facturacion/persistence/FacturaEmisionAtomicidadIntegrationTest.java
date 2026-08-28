package com.biopet.facturacion.persistence;

import com.biopet.entity.Usuario;
import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.ClaveAccesoGenerator;
import com.biopet.facturacion.domain.ClaveAccesoRequest;
import com.biopet.facturacion.domain.FormaPagoSri;
import com.biopet.facturacion.domain.TipoComprobante;
import com.biopet.facturacion.domain.TipoEmisionSri;
import com.biopet.facturacion.entity.ConceptoFacturable;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.entity.EmisorFiscal;
import com.biopet.facturacion.entity.EstadoFactura;
import com.biopet.facturacion.entity.Factura;
import com.biopet.facturacion.entity.PuntoEmision;
import com.biopet.facturacion.service.FacturaEmisionService;
import com.biopet.facturacion.service.command.CrearFacturaBorradorCommand;
import com.biopet.facturacion.service.command.DetalleBorradorCommand;
import com.biopet.facturacion.service.command.EmitirFacturaCommand;
import com.biopet.facturacion.service.command.PagoBorradorCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Atomicidad y concurrencia de la emision: que el contador y la factura vayan
 * SIEMPRE juntos, y que el mismo borrador no pueda numerarse dos veces.
 *
 * <p>Comparte contexto con {@link FacturaEmisionServiceIntegrationTest} (mismo
 * {@code @Import}) para no levantar otro contexto de Spring: el codigo numerico
 * determinista ademas hace falta aqui, porque uno de los tests necesita saber de
 * antemano que clave de acceso va a producir la emision.
 */
@SpringBootTest
@Import(CodigoNumericoDeterministaConfig.class)
class FacturaEmisionAtomicidadIntegrationTest extends FacturaEscenarioTestBase {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 15);

    @Autowired FacturaEmisionService emisionService;
    @Autowired ClaveAccesoGenerator claveAccesoGenerator;
    @Autowired PlatformTransactionManager transactionManager;

    // ==================================================================
    // Rollback
    // ==================================================================

    @Test
    void siLaTransaccionQueEmiteSeDeshaceElContadorVuelveAtrasConLaFactura() {
        // La emision usa propagacion REQUIRED, asi que se une a la transaccion
        // de quien llama: si esa falla, el numero reservado NO queda consumido.
        Escenario escenario = new Escenario();
        Factura borrador = escenario.borradorListoParaEmitir();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            Factura emitida = emisionService.emitir(new EmitirFacturaCommand(
                    borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS));
            assertThat(emitida.getSecuencial()).isEqualTo(1L);
            throw new IllegalStateException("fallo simulado despues de numerar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
        Factura releida = facturaRepository.findById(borrador.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.BORRADOR);
        assertThat(releida.getClaveAcceso()).isNull();
        assertThat(releida.getSecuencial()).isNull();
        assertThat(releida.getCodigoNumerico()).isNull();

        // Y el numero 1 sigue disponible: la siguiente emision lo usa.
        Factura emitida = emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS));
        assertThat(emitida.getSecuencial()).isEqualTo(1L);
        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isEqualTo(1L);
    }

    @Test
    void unFalloDePersistenciaDentroDeLaEmisionRevierteTambienElContador() {
        // Fallo real, provocado sin ningun hook de test en produccion: se
        // siembra otra factura que ya ocupa la clave de acceso que esta emision
        // va a componer, de modo que el indice unico idx_facturas_clave_acceso
        // salta en el flush, DESPUES de haber reservado el secuencial.
        Escenario escenario = new Escenario();
        Factura borrador = escenario.borradorListoParaEmitir();

        String claveOcupada = claveAccesoGenerator.generar(new ClaveAccesoRequest(
                FECHA, TipoComprobante.FACTURA, escenario.emisor.getRuc(), AmbienteSri.PRUEBAS,
                escenario.punto.getEstablecimiento(), escenario.punto.getPuntoEmision(),
                1L, CodigoNumericoDeterministaConfig.CODIGO, TipoEmisionSri.NORMAL));

        Usuario otro = nuevoUsuario();
        jdbc.update("INSERT INTO facturas (usuario_id, fecha_emision, estado, clave_acceso) "
                + "VALUES (?, DATE '2026-09-15', 'EMITIDA', ?)", otro.getId(), claveOcupada);

        assertThatThrownBy(() -> emisionService.emitir(new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // Lo que importa: el contador NO se quedo gastado.
        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isZero();
        Factura releida = facturaRepository.findById(borrador.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.BORRADOR);
        assertThat(releida.getClaveAcceso()).isNull();
    }

    // ==================================================================
    // Concurrencia sobre la MISMA factura
    // ==================================================================

    @Test
    void dosEmisionesSimultaneasDelMismoBorradorConsumenUnSoloNumero() throws Exception {
        Escenario escenario = new Escenario();
        Factura borrador = escenario.borradorListoParaEmitir();
        EmitirFacturaCommand orden = new EmitirFacturaCommand(
                borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS);

        int workers = 2;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch listos = new CountDownLatch(workers);
        CountDownLatch arranque = new CountDownLatch(1);
        List<Future<Factura>> futuros = new ArrayList<>();

        try {
            for (int i = 0; i < workers; i++) {
                futuros.add(executor.submit(() -> {
                    listos.countDown();
                    arranque.await(30, TimeUnit.SECONDS);
                    // Transaccion propia de cada worker: sin esto no habria
                    // concurrencia real, solo dos llamadas en el mismo hilo.
                    return new TransactionTemplate(transactionManager)
                            .execute(status -> emisionService.emitir(orden));
                }));
            }
            assertThat(listos.await(30, TimeUnit.SECONDS)).isTrue();
            arranque.countDown();

            List<Factura> resultados = new ArrayList<>();
            List<Throwable> errores = new ArrayList<>();
            for (Future<Factura> futuro : futuros) {
                try {
                    resultados.add(futuro.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    errores.add(e.getCause());
                }
            }

            // Con el bloqueo pesimista sobre la fila de la factura, el segundo
            // hilo espera al primero y encuentra la factura ya EMITIDA, asi que
            // la devuelve en lugar de numerar otra vez: ambos observan la misma
            // emision y ninguno falla.
            assertThat(errores).isEmpty();
            assertThat(resultados).hasSize(2);
            assertThat(resultados).extracting(Factura::getId)
                    .containsOnly(borrador.getId());
            assertThat(resultados).extracting(Factura::getSecuencial).containsOnly(1L);
            assertThat(resultados.get(0).getClaveAcceso())
                    .isEqualTo(resultados.get(1).getClaveAcceso());
        } finally {
            arranque.countDown();
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }

        // Lo indispensable: un unico numero consumido y una sola factura.
        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM facturas WHERE punto_emision_id = ?",
                Integer.class, escenario.punto.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT clave_acceso) FROM facturas WHERE punto_emision_id = ?",
                Integer.class, escenario.punto.getId())).isEqualTo(1);

        Factura releida = facturaRepository.findById(borrador.getId()).orElseThrow();
        assertThat(releida.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(releida.getSecuencial()).isEqualTo(1L);
    }

    @Test
    void dosBorradoresDistintosDelMismoPuntoSeNumeranEnSerieSinChocar() throws Exception {
        Escenario escenario = new Escenario();
        Factura uno = escenario.borradorListoParaEmitir();
        Factura dos = escenario.borradorListoParaEmitir();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch arranque = new CountDownLatch(1);
        try {
            List<Future<Factura>> futuros = new ArrayList<>();
            for (Factura borrador : List.of(uno, dos)) {
                futuros.add(executor.submit(() -> {
                    arranque.await(30, TimeUnit.SECONDS);
                    return new TransactionTemplate(transactionManager).execute(status ->
                            emisionService.emitir(new EmitirFacturaCommand(
                                    borrador.getId(), escenario.punto.getId(), AmbienteSri.PRUEBAS)));
                }));
            }
            arranque.countDown();

            List<Long> secuenciales = new ArrayList<>();
            for (Future<Factura> futuro : futuros) {
                secuenciales.add(futuro.get(60, TimeUnit.SECONDS).getSecuencial());
            }
            assertThat(secuenciales).containsExactlyInAnyOrder(1L, 2L);
        } finally {
            arranque.countDown();
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }

        assertThat(ultimoSecuencial(escenario.punto, AmbienteSri.PRUEBAS)).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT clave_acceso) FROM facturas WHERE punto_emision_id = ?",
                Integer.class, escenario.punto.getId())).isEqualTo(2);
    }

    // ==================================================================
    // Escenario
    // ==================================================================

    private final class Escenario {
        private final EmisorFiscal emisor = nuevoEmisor();
        private final PuntoEmision punto = nuevoPunto(emisor);
        private final ConceptoFacturable concepto;

        Escenario() {
            nuevoContador(punto, AmbienteSri.PRUEBAS, 0L);
            String codigoPorcentaje = nuevoCodigoPorcentaje();
            nuevaTarifa(codigoPorcentaje, "15.00", LocalDate.of(2020, 1, 1), null);
            concepto = nuevoConcepto(codigoPorcentaje, "20.000000");
        }

        /** Borrador con comprador, una linea de 2 x 20.00 y un pago de 46.00. */
        Factura borradorListoParaEmitir() {
            Usuario usuario = nuevoUsuario();
            DatosFacturacion datos = nuevosDatos(usuario);
            Factura borrador = borradorService.crear(
                    new CrearFacturaBorradorCommand(usuario.getId(), null, FECHA));
            borradorService.seleccionarComprador(borrador.getId(), datos.getId());
            borradorService.reemplazarDetalles(borrador.getId(),
                    List.of(DetalleBorradorCommand.de(concepto.getId(), new BigDecimal("2"))));
            borradorService.reemplazarPagos(borrador.getId(), List.of(
                    PagoBorradorCommand.de(FormaPagoSri.TARJETA_DEBITO, new BigDecimal("46.00"))));
            return facturaRepository.findById(borrador.getId()).orElseThrow();
        }
    }
}
