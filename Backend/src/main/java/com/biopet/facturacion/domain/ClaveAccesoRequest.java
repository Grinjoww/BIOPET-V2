package com.biopet.facturacion.domain;

import java.time.LocalDate;

/**
 * Entrada completa y ya validada para componer una clave de acceso (Ficha
 * v2.34, seccion 5.2, TABLA 1).
 *
 * <p>Toda la validacion vive en el constructor compacto: si el record se
 * construye, sus datos son aptos para producir una clave bien formada. Esto
 * hace estructuralmente imposible obtener una clave "parcialmente valida".
 */
public record ClaveAccesoRequest(
        LocalDate fechaEmision,
        TipoComprobante tipoComprobante,
        String ruc,
        AmbienteSri ambiente,
        String establecimiento,
        String puntoEmision,
        long secuencial,
        String codigoNumerico,
        TipoEmisionSri tipoEmision
) {

    public static final long SECUENCIAL_MINIMO = 1L;
    public static final long SECUENCIAL_MAXIMO = 999_999_999L;

    private static final int LONGITUD_RUC = 13;
    private static final int LONGITUD_ESTABLECIMIENTO = 3;
    private static final int LONGITUD_PUNTO_EMISION = 3;

    public ClaveAccesoRequest {
        if (fechaEmision == null) {
            throw new IllegalArgumentException("La fecha de emision es obligatoria.");
        }
        if (tipoComprobante == null) {
            throw new IllegalArgumentException("El tipo de comprobante es obligatorio.");
        }
        if (ambiente == null) {
            throw new IllegalArgumentException("El ambiente SRI es obligatorio.");
        }
        if (tipoEmision == null) {
            throw new IllegalArgumentException("El tipo de emision es obligatorio.");
        }

        // Solo se comprueba la FORMA que exige la clave de acceso (13 digitos).
        // No se aplica ningun "validador universal de RUC ecuatoriano": la
        // validez tributaria real del RUC la resuelve el propio SRI al
        // autorizar (errores 46 "RUC no existe" y 63 "RUC clausurado"), y no
        // existe un algoritmo unico publicado que se pueda imponer a todo
        // establecimiento.
        exigirDigitos(ruc, LONGITUD_RUC, "El RUC");
        exigirDigitos(establecimiento, LONGITUD_ESTABLECIMIENTO, "El establecimiento");
        exigirDigitos(puntoEmision, LONGITUD_PUNTO_EMISION, "El punto de emision");
        exigirDigitos(codigoNumerico, CodigoNumericoGenerator.LONGITUD, "El codigo numerico");

        if (secuencial < SECUENCIAL_MINIMO || secuencial > SECUENCIAL_MAXIMO) {
            throw new IllegalArgumentException(
                    "El secuencial debe estar entre " + SECUENCIAL_MINIMO + " y " + SECUENCIAL_MAXIMO
                            + "; se recibio " + secuencial + ".");
        }
    }

    /** Secuencial normalizado a los 9 digitos que exige la clave: 42 -> "000000042". */
    public String secuencialFormateado() {
        return String.format("%09d", secuencial);
    }

    private static void exigirDigitos(String valor, int longitud, String etiqueta) {
        if (valor == null || valor.length() != longitud) {
            throw new IllegalArgumentException(
                    etiqueta + " debe tener exactamente " + longitud + " digitos; se recibio "
                            + (valor == null ? "null" : "\"" + valor + "\"") + ".");
        }
        for (int i = 0; i < valor.length(); i++) {
            char caracter = valor.charAt(i);
            if (caracter < '0' || caracter > '9') {
                throw new IllegalArgumentException(
                        etiqueta + " solo admite digitos; se recibio \"" + valor + "\".");
            }
        }
    }
}
