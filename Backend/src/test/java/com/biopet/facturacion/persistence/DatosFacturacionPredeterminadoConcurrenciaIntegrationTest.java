package com.biopet.facturacion.persistence;

import com.biopet.entity.Usuario;
import com.biopet.facturacion.entity.DatosFacturacion;
import com.biopet.facturacion.service.DatosFacturacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia real sobre PostgreSQL para la seccion 6 de la Fase 8B: dos
 * transacciones INDEPENDIENTES intentan, a la vez, convertir en predeterminado
 * un perfil de facturacion DISTINTO del mismo usuario.
 *
 * <p>Igual que {@link SecuencialConcurrenciaIntegrationTest}, cada worker abre
 * su PROPIA transaccion con {@link TransactionTemplate} en su propio hilo -si
 * corriesen dentro de la transaccion del test compartirian conexion y no
 * habria contencion real- y arrancan a la vez tras una barrera.
 *
 * <p>Lo que se exige NO es que ambas peticiones tengan exito: es exactamente lo
 * contrario. {@code DatosFacturacionService#aplicarPredeterminado} no impide
 * por si solo la carrera (ver su javadoc); la garantia final es
 * {@code idx_datos_facturacion_predeterminado_unico} (V7), que debe dejar
 * pasar a una transaccion y hacer fallar a la otra con una violacion de
 * restriccion. El resultado observable, en ambos casos, es que la base NUNCA
 * queda con dos filas predeterminadas a la vez para el mismo usuario.
 */
@SpringBootTest
class DatosFacturacionPredeterminadoConcurrenciaIntegrationTest extends FacturaEscenarioTestBase {

    @Autowired DatosFacturacionService datosFacturacionService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void dosPeticionesConcurrentesSobreDistintoDestinoNuncaDejanDosPredeterminados() throws Exception {
        Usuario dueno = nuevoUsuario();
        DatosFacturacion actual = nuevosDatos(dueno);
        jdbc.update("UPDATE datos_facturacion SET predeterminado = TRUE WHERE id = ?", actual.getId());
        DatosFacturacion candidatoA = nuevosDatos(dueno);
        DatosFacturacion candidatoB = nuevosDatos(dueno);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranque = new CountDownLatch(1);

        List<Future<Throwable>> futuros = new ArrayList<>(2);
        for (Long objetivo : List.of(candidatoA.getId(), candidatoB.getId())) {
            futuros.add(executor.submit(() -> {
                listos.countDown();
                arranque.await(30, TimeUnit.SECONDS);
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                            datosFacturacionService.marcarPredeterminado(dueno.getId(), objetivo, dueno.getEmail()));
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }

        assertThat(listos.await(30, TimeUnit.SECONDS)).as("ambos workers listos").isTrue();
        arranque.countDown();

        List<Throwable> errores = new ArrayList<>();
        for (Future<Throwable> futuro : futuros) {
            Throwable resultado = futuro.get(60, TimeUnit.SECONDS);
            if (resultado != null) {
                errores.add(resultado);
            }
        }
        executor.shutdownNow();

        // Al menos una debe ganar (si no, algo distinto a la carrera esperada
        // fallo); como mucho una puede perder por la violacion del indice unico.
        assertThat(errores).hasSizeLessThanOrEqualTo(1);

        Long predeterminados = jdbc.queryForObject(
                "SELECT COUNT(*) FROM datos_facturacion WHERE usuario_id = ? AND predeterminado = TRUE",
                Long.class, dueno.getId());
        assertThat(predeterminados).as("nunca debe haber dos predeterminados a la vez").isEqualTo(1L);
    }
}
