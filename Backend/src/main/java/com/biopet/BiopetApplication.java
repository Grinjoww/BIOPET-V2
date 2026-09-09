package com.biopet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: unicamente para RespaldoScheduler (modulo de
// respaldos). Es una revision periodica LIVIANA de configuracion, no un
// scheduler de negocio con fixedRate igual al intervalo elegido por el
// ADMIN -ver el javadoc de RespaldoScheduler.
@EnableCaching
@EnableScheduling
@SpringBootApplication
public class BiopetApplication {
    public static void main(String[] args) {
        SpringApplication.run(BiopetApplication.class, args);
    }
}
