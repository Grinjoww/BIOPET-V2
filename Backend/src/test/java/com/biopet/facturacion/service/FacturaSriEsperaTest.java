package com.biopet.facturacion.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Politica de espera entre reintentos de autorizacion.
 *
 * <p>Se prueba sola, sin base de datos ni red, porque es aritmetica pura y
 * porque las dos propiedades que importan son faciles de romper sin darse
 * cuenta: que CREZCA (para no castigar a un SRI que ya va lento) y que tenga
 * TECHO (para que una factura pendiente no acabe consultandose una vez al dia).
 *
 * <p>Nada de esto duerme dentro de una peticion: solo calcula la marca
 * {@code proximo_intento_en} que leera quien reintente.
 */
class FacturaSriEsperaTest {

    @Test
    void elPrimerIntentoEsperaLoMinimo() {
        assertThat(FacturaSriEstadoService.espera(1)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void laEsperaSeDuplicaConCadaIntento() {
        assertThat(FacturaSriEstadoService.espera(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(FacturaSriEstadoService.espera(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(FacturaSriEstadoService.espera(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(FacturaSriEstadoService.espera(5)).isEqualTo(Duration.ofMinutes(16));
    }

    @Test
    void nuncaSuperaElTecho() {
        assertThat(FacturaSriEstadoService.espera(6)).isEqualTo(Duration.ofMinutes(30));
        assertThat(FacturaSriEstadoService.espera(50)).isEqualTo(Duration.ofMinutes(30));
        // Sin el tope del exponente, 1 << (10000-1) desbordaria y podria dar
        // una espera negativa o absurda.
        assertThat(FacturaSriEstadoService.espera(10_000)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void unContadorAbsurdoNoProduceEsperasNegativas() {
        assertThat(FacturaSriEstadoService.espera(0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(FacturaSriEstadoService.espera(-5)).isEqualTo(Duration.ofMinutes(1));
    }
}
