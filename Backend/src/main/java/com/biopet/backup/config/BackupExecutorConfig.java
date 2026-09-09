package com.biopet.backup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Un unico hilo dedicado a ejecutar pg_dump en segundo plano. Deliberadamente
 * de un solo hilo: junto con el AtomicBoolean de
 * RespaldoEjecucionService, es la segunda mitad de la garantia de "nunca dos
 * respaldos a la vez" -aunque en la practica el AtomicBoolean ya rechaza la
 * segunda solicitud antes de que llegue a encolarse aqui.
 *
 * <p>Hilo daemon: si el proceso JVM termina abruptamente a mitad de un
 * pg_dump, no lo mantiene vivo. destroyMethod="shutdown" libera el hilo de
 * forma ordenada al apagar el contexto de Spring.
 */
@Configuration
public class BackupExecutorConfig {

    @Bean(name = "respaldoExecutor", destroyMethod = "shutdown")
    public ExecutorService respaldoExecutor() {
        ThreadFactory fabricaHilos = new ThreadFactory() {
            private final AtomicInteger contador = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread hilo = new Thread(r, "respaldo-pgdump-" + contador.getAndIncrement());
                hilo.setDaemon(true);
                return hilo;
            }
        };
        return Executors.newSingleThreadExecutor(fabricaHilos);
    }
}
