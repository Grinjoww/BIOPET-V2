package com.biopet.facturacion.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuloOnceTest {

    /**
     * Prueba de referencia: es el ejemplo publicado por el SRI en la Ficha
     * v2.34, seccion 5.2. Productos 4x3+1x2+2x7+6x6+1x5+5x4+3x3+3x2 = 104;
     * 104 mod 11 = 5; 11 - 5 = 6.
     */
    @Test
    void ejemploOficialDeLaFichaDevuelveSeis() {
        assertEquals(6, ModuloOnce.digitoVerificador("41261533"));
    }

    /** Caso especial de la ficha: resultado 11 -> el digito verificador es 0. */
    @Test
    void cuandoElResultadoEsOnceElDigitoEsCero() {
        // 4x2 + 1x3 = 11 -> 11 mod 11 = 0 -> 11 - 0 = 11 -> 0
        assertEquals(0, ModuloOnce.digitoVerificador("00000014"));
    }

    /** Caso especial de la ficha: resultado 10 -> el digito verificador es 1. */
    @Test
    void cuandoElResultadoEsDiezElDigitoEsUno() {
        // 6x2 = 12 -> 12 mod 11 = 1 -> 11 - 1 = 10 -> 1
        assertEquals(1, ModuloOnce.digitoVerificador("00000006"));
    }

    @Test
    void losFactoresCiclanDelDosAlSieteDesdeLaDerecha() {
        // Un unico 1 en la posicion 7 desde la derecha reinicia el ciclo a 2.
        assertEquals(11 - 2, ModuloOnce.digitoVerificador("1000000"));
        // Un unico 1 en la posicion 6 desde la derecha usa el factor 7.
        assertEquals(11 - 7, ModuloOnce.digitoVerificador("100000"));
    }

    @Test
    void cadenaDeCerosDevuelveCero() {
        assertEquals(0, ModuloOnce.digitoVerificador("00000000"));
    }

    @Test
    void siempreDevuelveUnDigitoEntreCeroYNueve() {
        for (int n = 0; n < 5000; n++) {
            int digito = ModuloOnce.digitoVerificador(String.valueOf(n));
            org.junit.jupiter.api.Assertions.assertTrue(digito >= 0 && digito <= 9,
                    "Digito fuera de rango para " + n + ": " + digito);
        }
    }

    @Test
    void rechazaCadenaNula() {
        assertThrows(IllegalArgumentException.class, () -> ModuloOnce.digitoVerificador(null));
    }

    @Test
    void rechazaCadenaVacia() {
        assertThrows(IllegalArgumentException.class, () -> ModuloOnce.digitoVerificador(""));
    }

    @Test
    void rechazaCadenaNoNumerica() {
        assertThrows(IllegalArgumentException.class, () -> ModuloOnce.digitoVerificador("4126153A"));
        assertThrows(IllegalArgumentException.class, () -> ModuloOnce.digitoVerificador("4126 533"));
        assertThrows(IllegalArgumentException.class, () -> ModuloOnce.digitoVerificador("-1261533"));
    }
}
