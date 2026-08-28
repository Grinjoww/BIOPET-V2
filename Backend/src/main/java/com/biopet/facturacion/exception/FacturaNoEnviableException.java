package com.biopet.facturacion.exception;

/**
 * La factura no esta en condiciones de dialogar con el SRI.
 *
 * <p>Cubre los casos en los que ni siquiera tiene sentido abrir la conexion:
 * un BORRADOR (no tiene clave de acceso ni secuencial, no existe fiscalmente),
 * una factura ya RECHAZADA de forma definitiva, o una EMITIDA sin clave de
 * acceso, que seria un dato corrupto.
 *
 * <p>Es distinta de {@code SriComunicacionException}: alli el problema esta en
 * la red o en el SRI; aqui el problema es que se pidio algo que no procede, y
 * reintentarlo no lo va a arreglar.
 */
public class FacturaNoEnviableException extends RuntimeException {

    public FacturaNoEnviableException(String mensaje) {
        super(mensaje);
    }
}
