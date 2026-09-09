package com.biopet.backup.pgdump;

import com.biopet.backup.config.BackupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Unica implementacion real de PgDumpExecutor: lanza {@code pg_dump -Fc}
 * como proceso externo via ProcessBuilder.
 *
 * <p>REGLA CRITICA que gobierna esta clase entera: la contrasena de
 * PostgreSQL NUNCA aparece en la lista de argumentos (visible en
 * {@code ps}/Task Manager de cualquier otro proceso del mismo host) ni en
 * ningun log. La UNICA forma en que este codigo la toca es escribiendola en
 * el entorno del proceso hijo (PGPASSWORD) -que pg_dump lee y libpq borra de
 * su propio entorno tan pronto la usa- justo antes de {@code start()}, y
 * nunca se le da otro uso.
 */
@Component
public class PgDumpExecutorImpl implements PgDumpExecutor {

    private static final Logger log = LoggerFactory.getLogger(PgDumpExecutorImpl.class);

    /** Cuantas lineas de stderr se leen como maximo para el log del SERVIDOR (nunca para la BD/HTTP). */
    private static final int MAX_LINEAS_STDERR_LOG = 20;

    /** "pg_dump --version" es casi instantaneo; 10s ya es generoso para detectar un ejecutable colgado. */
    private static final long TIMEOUT_VALIDACION_SEGUNDOS = 10;

    private final BackupProperties properties;

    public PgDumpExecutorImpl(BackupProperties properties) {
        this.properties = properties;
    }

    @Override
    public void ejecutar(PgDumpParametros parametros) {
        validarPgDumpDisponible();

        List<String> comando = construirComando(parametros);
        // Seguro de loguear: "comando" jamas contiene la contrasena (ver
        // javadoc de la clase), solo host/puerto/usuario/ruta de salida.
        log.debug("Lanzando pg_dump: {}", comando);

        ProcessBuilder procesoBuilder = new ProcessBuilder(comando);
        procesoBuilder.environment().put("PGPASSWORD", parametros.password());
        procesoBuilder.redirectErrorStream(false);

        Process proceso;
        try {
            proceso = procesoBuilder.start();
        } catch (IOException e) {
            log.error("No se pudo iniciar el proceso pg_dump (ejecutable configurado: '{}')",
                    properties.getPgDumpPath(), e);
            throw new PgDumpExecutionException(TipoFalloPgDump.ERROR_E_S,
                    "No se pudo iniciar el proceso pg_dump.", e);
        }

        boolean terminoATiempo;
        try {
            terminoATiempo = proceso.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            proceso.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new PgDumpExecutionException(TipoFalloPgDump.INTERRUMPIDO,
                    "La ejecucion de pg_dump fue interrumpida.", e);
        }

        if (!terminoATiempo) {
            proceso.destroyForcibly();
            log.error("pg_dump no finalizo dentro de {} segundos; proceso cancelado.",
                    properties.getTimeoutSeconds());
            throw new PgDumpExecutionException(TipoFalloPgDump.TIMEOUT,
                    "pg_dump no finalizo dentro del tiempo limite configurado.");
        }

        int codigoSalida = proceso.exitValue();
        if (codigoSalida != 0) {
            String resumenStderr = leerResumen(proceso.getErrorStream());
            log.error("pg_dump finalizo con codigo de salida {}. stderr (primeras lineas): {}",
                    codigoSalida, resumenStderr);
            throw new PgDumpExecutionException(TipoFalloPgDump.CODIGO_SALIDA,
                    "pg_dump finalizo con codigo de salida " + codigoSalida + ".");
        }
    }

    /**
     * Portabilidad (PC del examen): valida ANTES del respaldo real que
     * {@code backup.pg-dump-path} (env {@code PG_DUMP_PATH}, o
     * {@code pg_dump} del PATH del sistema si no se configuro nada) apunte a
     * un ejecutable que realmente arranca. Es exactamente el problema real
     * ya observado en esta base: un pg_dump de una version de PostgreSQL
     * distinta a la del servidor (p.ej. 16 en el PATH contra un servidor 18)
     * se detecta igual, pero mas tarde y con un mensaje generico -esta
     * validacion cubre el caso mas comun en una maquina nueva: el ejecutable
     * simplemente no existe en la ruta configurada. Si "pg_dump --version"
     * SI arranca pero el servidor termina rechazando el respaldo por
     * incompatibilidad real de version, eso lo sigue reportando el propio
     * pg_dump al ejecutar el respaldo (ver el bloque de abajo), con el mismo
     * resultado seguro: FALLIDO, sin tumbar BIOPET.
     */
    private void validarPgDumpDisponible() {
        List<String> comandoVersion = List.of(properties.getPgDumpPath(), "--version");
        ProcessBuilder builder = new ProcessBuilder(comandoVersion);
        builder.redirectErrorStream(true);

        Process proceso;
        try {
            proceso = builder.start();
        } catch (IOException e) {
            log.error("pg_dump no esta disponible en la ruta configurada ('{}'). "
                    + "Configure la variable de entorno PG_DUMP_PATH con la ruta completa al "
                    + "ejecutable, o instale un cliente PostgreSQL compatible en el PATH del sistema.",
                    properties.getPgDumpPath(), e);
            throw new PgDumpExecutionException(TipoFalloPgDump.EJECUTABLE_NO_DISPONIBLE,
                    "No se encontro un ejecutable de pg_dump valido. Configure la variable de "
                    + "entorno PG_DUMP_PATH.");
        }

        String salida;
        boolean terminoATiempo;
        try {
            terminoATiempo = proceso.waitFor(TIMEOUT_VALIDACION_SEGUNDOS, TimeUnit.SECONDS);
            salida = leerResumen(proceso.getInputStream());
        } catch (InterruptedException e) {
            proceso.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new PgDumpExecutionException(TipoFalloPgDump.INTERRUMPIDO,
                    "La verificacion de pg_dump --version fue interrumpida.");
        }

        if (!terminoATiempo) {
            proceso.destroyForcibly();
            log.error("pg_dump --version no respondio dentro de {} s (ruta configurada: '{}').",
                    TIMEOUT_VALIDACION_SEGUNDOS, properties.getPgDumpPath());
            throw new PgDumpExecutionException(TipoFalloPgDump.EJECUTABLE_NO_DISPONIBLE,
                    "El ejecutable de pg_dump configurado no respondio a tiempo.");
        }
        if (proceso.exitValue() != 0) {
            log.error("pg_dump --version devolvio codigo {} (ruta configurada: '{}'): {}",
                    proceso.exitValue(), properties.getPgDumpPath(), salida);
            throw new PgDumpExecutionException(TipoFalloPgDump.EJECUTABLE_NO_DISPONIBLE,
                    "El ejecutable de pg_dump configurado no es valido.");
        }
        // Seguro de loguear: solo el texto de version que el propio pg_dump imprime, nunca un secreto.
        log.info("pg_dump disponible: {}", salida.trim());
    }

    private List<String> construirComando(PgDumpParametros p) {
        List<String> comando = new ArrayList<>();
        comando.add(properties.getPgDumpPath());
        comando.add("-h");
        comando.add(p.host());
        comando.add("-p");
        comando.add(String.valueOf(p.port()));
        comando.add("-U");
        comando.add(p.usuario());
        comando.add("-F");
        comando.add("c");
        comando.add("-f");
        comando.add(p.rutaSalida().toString());
        comando.add(p.database());
        return comando;
    }

    /** Solo para el log del servidor -nunca llega a la base ni a una respuesta HTTP. */
    private String leerResumen(InputStream flujo) {
        StringBuilder resumen = new StringBuilder();
        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(flujo, StandardCharsets.UTF_8))) {
            String linea;
            int contadas = 0;
            while (contadas < MAX_LINEAS_STDERR_LOG && (linea = lector.readLine()) != null) {
                resumen.append(linea).append(System.lineSeparator());
                contadas++;
            }
        } catch (IOException e) {
            resumen.append("(no se pudo leer stderr: ").append(e.getMessage()).append(')');
        }
        return resumen.toString();
    }
}
