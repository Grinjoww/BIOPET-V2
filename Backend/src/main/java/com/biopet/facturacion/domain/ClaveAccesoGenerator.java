package com.biopet.facturacion.domain;

import java.time.format.DateTimeFormatter;

/**
 * Compone la clave de acceso de 49 digitos del esquema offline del SRI (Ficha
 * v2.34, seccion 5.2, TABLA 1).
 *
 * <p>Orden y longitud de los campos, exactamente como los publica la TABLA 1:
 * <pre>
 *   1. Fecha de emision      ddmmaaaa   8
 *   2. Tipo de comprobante   TABLA 3    2
 *   3. Numero de RUC                   13
 *   4. Tipo de ambiente      TABLA 4    1
 *   5. Serie (estab+ptoEmi)             6
 *   6. Secuencial                       9
 *   7. Codigo numerico                  8
 *   8. Tipo de emision       TABLA 2    1
 *   9. Digito verificador    modulo 11  1
 *                                      --
 *                                      49
 * </pre>
 *
 * <p>La ficha advierte que "todos los campos deben completarse conforme a la
 * longitud indicada, es decir si en el numero secuencial no completa los 9
 * digitos, la clave de acceso estara mal conformada y sera motivo de rechazo
 * para su autorizacion".
 *
 * <p>Esta clase es pura: no reserva ni incrementa secuenciales (eso es
 * responsabilidad de PostgreSQL en una fase posterior) y no sortea el codigo
 * numerico (ver {@link CodigoNumericoGenerator}). Recibe todo como dato para
 * que una misma entrada produzca siempre la misma clave, que es justo lo que
 * permite reintentar ante el SRI sin cambiarla.
 */
public class ClaveAccesoGenerator {

    public static final int LONGITUD_CLAVE = 49;
    public static final int LONGITUD_BASE = 48;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("ddMMyyyy");

    public String generar(ClaveAccesoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de clave de acceso no puede ser nula.");
        }

        String base = request.fechaEmision().format(FORMATO_FECHA)
                + request.tipoComprobante().codDoc()
                + request.ruc()
                + request.ambiente().codigo()
                + request.establecimiento()
                + request.puntoEmision()
                + request.secuencialFormateado()
                + request.codigoNumerico()
                + request.tipoEmision().codigo();

        if (base.length() != LONGITUD_BASE) {
            throw new IllegalStateException(
                    "La base de la clave de acceso debe tener " + LONGITUD_BASE + " digitos; se obtuvo "
                            + base.length() + ".");
        }

        return base + ModuloOnce.digitoVerificador(base);
    }

    /**
     * Recalcula el digito verificador sobre los primeros 48 digitos y lo compara
     * con el ultimo. Util para verificar una clave persistida o recibida.
     */
    public boolean esValida(String claveAcceso) {
        if (claveAcceso == null || claveAcceso.length() != LONGITUD_CLAVE) {
            return false;
        }
        for (int i = 0; i < claveAcceso.length(); i++) {
            char caracter = claveAcceso.charAt(i);
            if (caracter < '0' || caracter > '9') {
                return false;
            }
        }
        int esperado = ModuloOnce.digitoVerificador(claveAcceso.substring(0, LONGITUD_BASE));
        return esperado == (claveAcceso.charAt(LONGITUD_BASE) - '0');
    }
}
