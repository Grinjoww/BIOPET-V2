package com.biopet.backup.pgdump;

import com.biopet.backup.config.BackupProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Portabilidad (riesgo ya observado en esta base: un pg_dump 16 del PATH
 * fallando contra un servidor 18). Estos tests cubren la validacion previa
 * "pg_dump --version" que ahora corre ANTES de cada respaldo real -la unica
 * pieza del modulo que de verdad lanza un proceso del sistema operativo, y
 * por eso la unica que no se puede probar solo con PgDumpExecutor mockeado
 * (ver RespaldoEjecucionServiceTest, que mockea la interfaz para todo lo
 * demas).
 */
class PgDumpExecutorImplTest {

    @TempDir
    Path directorioTemporal;

    @Test
    void ejecutableInexistenteFallaEnLaValidacionPreviaSinIntentarElRespaldoReal() {
        BackupProperties properties = new BackupProperties();
        properties.setPgDumpPath("/ruta/que/definitivamente/no/existe/pg_dump_biopet_test_xyz");
        properties.setTimeoutSeconds(5);
        PgDumpExecutorImpl executor = new PgDumpExecutorImpl(properties);

        Path archivoSalida = directorioTemporal.resolve("no-deberia-crearse.dump");
        PgDumpParametros parametros = new PgDumpParametros(
                "localhost", 5432, "biopet_test", "usuario", "clave-de-prueba", archivoSalida);

        assertThatThrownBy(() -> executor.ejecutar(parametros))
                .isInstanceOf(PgDumpExecutionException.class)
                .satisfies(ex -> assertThat(((PgDumpExecutionException) ex).getTipo())
                        .isEqualTo(TipoFalloPgDump.EJECUTABLE_NO_DISPONIBLE));

        // La validacion previa debio detener todo ANTES de intentar el respaldo real.
        assertThat(Files.exists(archivoSalida)).isFalse();
    }

    @Test
    void mensajeDeErrorEsGenericoYMencionaLaVariableDeConfiguracion() {
        BackupProperties properties = new BackupProperties();
        properties.setPgDumpPath("/ruta/que/definitivamente/no/existe/pg_dump_biopet_test_xyz");
        PgDumpExecutorImpl executor = new PgDumpExecutorImpl(properties);
        PgDumpParametros parametros = new PgDumpParametros(
                "localhost", 5432, "biopet_test", "usuario", "clave-de-prueba",
                directorioTemporal.resolve("x.dump"));

        assertThatThrownBy(() -> executor.ejecutar(parametros))
                .isInstanceOf(PgDumpExecutionException.class)
                .hasMessageContaining("PG_DUMP_PATH")
                // Nunca debe exponer la ruta interna exacta ni un stack trace crudo en el mensaje seguro.
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("clave-de-prueba"));
    }
}
