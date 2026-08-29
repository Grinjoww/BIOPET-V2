package com.biopet.facturacion.exception;

/**
 * El RIDE (representacion impresa) de una factura se pidio antes de que la
 * factura estuviese en condiciones de tenerlo.
 *
 * <p>El RIDE es la representacion de un comprobante YA AUTORIZADO por el SRI:
 * no existe "RIDE de un borrador" ni "RIDE de una factura emitida pero sin
 * respuesta del SRI todavia". Cubre BORRADOR, EMITIDA (incluso con XML firmado
 * y enviado, mientras no llegue AUT) y RECHAZADA, y tambien el caso -en teoria
 * imposible por la propia maquina de estados, pero que se comprueba igual- de
 * una factura AUTORIZADA a la que le falte alguno de los datos de autorizacion
 * que el RIDE debe imprimir (numero de autorizacion, fecha de autorizacion o
 * clave de acceso).
 *
 * <p>Es distinta de {@code FacturaNoEnviableException}: alli el problema es
 * dialogar con el SRI; aqui es imprimir algo que el SRI todavia no concedio.
 */
public class RideNoDisponibleException extends RuntimeException {

    public RideNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
