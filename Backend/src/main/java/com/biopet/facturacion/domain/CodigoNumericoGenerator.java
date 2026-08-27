package com.biopet.facturacion.domain;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * Genera el "codigo numerico" de 8 digitos de la clave de acceso (Ficha v2.34,
 * seccion 5.2, campo 7 de la TABLA 1).
 *
 * <p>La ficha describe este campo como "un mecanismo para brindar seguridad al
 * emisor en cada comprobante emitido" y aclara que "el algoritmo numerico para
 * conformar este codigo es potestad absoluta del contribuyente emisor". Por eso
 * se sortea uniformemente en el rango completo 00000000-99999999; no hay ninguna
 * regla del SRI que excluya valores concretos.
 *
 * <p>Esta generacion vive deliberadamente <b>fuera</b> de
 * {@link ClaveAccesoGenerator}: el codigo numerico se sortea una sola vez, al
 * emitir, y se persiste junto a la clave. Los reintentos ante el SRI reutilizan
 * el codigo ya guardado, de modo que la clave de acceso nunca cambia (Ficha
 * v2.34, seccion 5.10 y seccion 11, nota 1). Si el sorteo ocurriese dentro de
 * la composicion de la clave, cada reintento produciria una clave distinta.
 *
 * <p>El constructor que recibe un {@link RandomGenerator} existe para poder
 * fijar una semilla en las pruebas; el constructor por defecto usa
 * {@link SecureRandom}.
 */
public class CodigoNumericoGenerator {

    public static final int LONGITUD = 8;
    private static final int LIMITE_EXCLUSIVO = 100_000_000;

    private final RandomGenerator random;

    public CodigoNumericoGenerator() {
        this(new SecureRandom());
    }

    public CodigoNumericoGenerator(RandomGenerator random) {
        if (random == null) {
            throw new IllegalArgumentException("El generador aleatorio no puede ser nulo.");
        }
        this.random = random;
    }

    /** @return exactamente 8 digitos, con ceros a la izquierda si hace falta. */
    public String generar() {
        return String.format("%08d", random.nextInt(LIMITE_EXCLUSIVO));
    }
}
