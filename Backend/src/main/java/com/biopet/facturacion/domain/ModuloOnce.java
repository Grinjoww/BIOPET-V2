package com.biopet.facturacion.domain;

/**
 * Digito verificador "Modulo 11" con factor de chequeo ponderado, tal como lo
 * define la Ficha Tecnica de Comprobantes Electronicos Offline v2.34, seccion
 * 5.2.
 *
 * <p>Algoritmo, textual de la ficha: el digito verificador "sera aplicado sobre
 * toda la clave de acceso (48 digitos)"; los factores van del 2 al 7 y se
 * aplican <b>de derecha a izquierda</b>, reiniciando el ciclo en 2 cuando se
 * agota el 7. Se suman los productos, se toma el modulo 11 y el digito es
 * {@code 11 - modulo}, con dos casos especiales que la ficha enuncia
 * explicitamente: "Cuando el resultado del digito verificador obtenido sea
 * igual a once (11), el digito verificador sera el cero (0) y cuando el
 * resultado del digito verificador obtenido sea igual a diez 10, el digito
 * verificador sera el uno (1)".
 *
 * <p>Ejemplo oficial de la misma seccion, reproducido como prueba de
 * referencia: para la cadena {@code 41261533} los productos son
 * {@code 4x3 + 1x2 + 2x7 + 6x6 + 1x5 + 5x4 + 3x3 + 3x2 = 104};
 * {@code 104 mod 11 = 5}; {@code 11 - 5 = 6}. Resultado: <b>6</b>.
 */
public final class ModuloOnce {

    private static final int FACTOR_MINIMO = 2;
    private static final int FACTOR_MAXIMO = 7;

    private ModuloOnce() {
    }

    /**
     * @param cadena secuencia no vacia de digitos (0-9) sobre la que calcular
     *               el verificador.
     * @return un unico digito, 0-9.
     * @throws IllegalArgumentException si la cadena es nula, vacia o contiene
     *                                  algo que no sea un digito decimal.
     */
    public static int digitoVerificador(String cadena) {
        if (cadena == null || cadena.isEmpty()) {
            throw new IllegalArgumentException("La cadena para el modulo 11 no puede ser nula ni vacia.");
        }

        int suma = 0;
        int factor = FACTOR_MINIMO;
        for (int i = cadena.length() - 1; i >= 0; i--) {
            char caracter = cadena.charAt(i);
            if (caracter < '0' || caracter > '9') {
                throw new IllegalArgumentException(
                        "La cadena para el modulo 11 solo admite digitos; se encontro '" + caracter + "'.");
            }
            suma += (caracter - '0') * factor;
            factor = (factor == FACTOR_MAXIMO) ? FACTOR_MINIMO : factor + 1;
        }

        int digito = 11 - (suma % 11);
        if (digito == 11) {
            return 0;
        }
        if (digito == 10) {
            return 1;
        }
        return digito;
    }
}
