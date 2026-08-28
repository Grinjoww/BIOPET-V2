package com.biopet.facturacion.exception;

/**
 * El SRI devolvio, para la misma clave de acceso, una autorizacion cuyos campos
 * MATERIALES (estado, numero de autorizacion, fecha o el comprobante
 * autorizado) no coinciden con el {@code XML_AUTORIZADO} ya persistido.
 *
 * <p>No deberia ocurrir nunca en un esquema offline correcto: una clave
 * autorizada una vez queda autorizada para siempre, con el mismo numero. Si
 * ocurre, es evidencia de un problema serio -una respuesta corrupta, una
 * confusion de ambientes, o dos comprobantes distintos compartiendo clave- y la
 * reaccion correcta es fallar de forma ruidosa, nunca sobrescribir en silencio
 * el documento archivado que ya se entrego como prueba de autorizacion.
 */
public class AutorizacionSriInconsistenteException extends RuntimeException {

    public AutorizacionSriInconsistenteException(String mensaje) {
        super(mensaje);
    }
}
