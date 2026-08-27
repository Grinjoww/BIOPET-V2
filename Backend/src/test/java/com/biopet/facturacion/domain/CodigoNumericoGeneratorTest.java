package com.biopet.facturacion.domain;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodigoNumericoGeneratorTest {

    @Test
    void generaExactamenteOchoDigitos() {
        CodigoNumericoGenerator generador = new CodigoNumericoGenerator(new Random(20260827L));
        for (int i = 0; i < 10_000; i++) {
            String codigo = generador.generar();
            assertEquals(8, codigo.length(), "Longitud incorrecta: " + codigo);
            for (int j = 0; j < codigo.length(); j++) {
                char caracter = codigo.charAt(j);
                assertTrue(caracter >= '0' && caracter <= '9', "Caracter no numerico en: " + codigo);
            }
        }
    }

    @Test
    void conSemillaFijaEsReproducible() {
        String primero = new CodigoNumericoGenerator(new Random(42L)).generar();
        String segundo = new CodigoNumericoGenerator(new Random(42L)).generar();
        assertEquals(primero, segundo);
    }

    @Test
    void rellenaConCerosALaIzquierda() {
        // Random fijo que siempre devuelve 7 -> "00000007".
        CodigoNumericoGenerator generador = new CodigoNumericoGenerator(new Random() {
            @Override
            public int nextInt(int bound) {
                return 7;
            }
        });
        assertEquals("00000007", generador.generar());
    }

    @Test
    void elConstructorPorDefectoUsaSecureRandomYProduceCodigoValido() {
        String codigo = new CodigoNumericoGenerator().generar();
        assertEquals(8, codigo.length());
        assertTrue(codigo.chars().allMatch(Character::isDigit));
    }

    @Test
    void rechazaGeneradorNulo() {
        assertThrows(IllegalArgumentException.class, () -> new CodigoNumericoGenerator(null));
    }
}
