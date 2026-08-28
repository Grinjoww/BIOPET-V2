package com.biopet.facturacion.persistence;

import com.biopet.facturacion.domain.CodigoNumericoGenerator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.random.RandomGenerator;

/**
 * Sustituye el generador de codigo numerico por uno que siempre devuelve
 * {@value #CODIGO}, para poder afirmar la clave de acceso EXACTA, digito a
 * digito, en lugar de limitarse a comprobar que mide 49 caracteres.
 *
 * <p>Asi es como se consigue determinismo sin ensuciar produccion: el servicio
 * no sabe nada de perfiles ni de tests, y no hay ningun
 * {@code if (perfil == test)} dentro de la logica de emision. Solo se cambia una
 * pieza por otra, que es justo para lo que
 * {@link CodigoNumericoGenerator#CodigoNumericoGenerator(RandomGenerator)}
 * existe desde la Fase 2. Tampoco hace falta reflexion.
 *
 * <p>En produccion sigue mandando el bean de
 * {@code FacturacionDomainConfig}, que usa {@code SecureRandom}: el codigo
 * numerico debe ser impredecible.
 */
@TestConfiguration
public class CodigoNumericoDeterministaConfig {

    public static final String CODIGO = "12345678";

    @Bean
    @Primary
    public CodigoNumericoGenerator codigoNumericoGeneratorDeterminista() {
        return new CodigoNumericoGenerator(new RandomGenerator() {
            @Override
            public long nextLong() {
                return Long.parseLong(CODIGO);
            }

            @Override
            public int nextInt(int bound) {
                return Integer.parseInt(CODIGO);
            }
        });
    }
}
